package com.snp.bookstorebio.data.source.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Mã hóa refresh_token bằng AES key nằm trong Android Keystore, key này CHỈ dùng được
 * sau khi user xác thực vân tay/Face/PIN thành công trong cùng phiên BiometricPrompt
 * (setUserAuthenticationRequired). Private/secret key không bao giờ rời khỏi Keystore —
 * app chỉ nhận về ciphertext, không tự giải mã được nếu thiếu xác thực sinh trắc học.
 *
 * Mỗi tài khoản (username) có key alias + bản ghi ciphertext RIÊNG — giống cách các app
 * thực tế làm (banking app...): đăng xuất không xoá vault của user đó, để lần sau đăng
 * nhập lại đúng tài khoản này trên cùng thiết bị vẫn dùng lại được vân tay ngay. Nhiều
 * tài khoản có thể cùng bật vân tay song song trên 1 thiết bị mà không ghi đè nhau.
 */
class BiometricVault(context: Context) {
    private val prefs = context.getSharedPreferences("biometric_vault", Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    /** Username đăng nhập gần nhất trên thiết bị này — dùng để tự chọn đúng vault lúc mở app. */
    fun lastUsername(): String? = prefs.getString(KEY_LAST_USERNAME, null)

    fun setLastUsername(username: String) {
        prefs.edit { putString(KEY_LAST_USERNAME, username) }
    }

    fun hasStoredToken(username: String): Boolean = prefs.contains(ciphertextKey(username))

    /** Cờ user bật/tắt qua nút trong app, RIÊNG cho từng tài khoản. */
    fun isBiometricEnabled(username: String): Boolean = prefs.getBoolean(enabledKey(username), false)

    fun setBiometricEnabled(username: String, enabled: Boolean) {
        prefs.edit { putBoolean(enabledKey(username), enabled) }
    }

    /** Xoá vault của ĐÚNG MỘT tài khoản — không đụng tới vault của tài khoản khác trên máy. */
    fun clear(username: String) {
        prefs.edit {
            remove(ciphertextKey(username))
            remove(ivKey(username))
            putBoolean(enabledKey(username), false)
        }
        runCatching { keyStore.deleteEntry(keyAlias(username)) }
    }

    /** Cipher ở chế độ ENCRYPT, sẵn sàng bọc trong BiometricPrompt.CryptoObject để xác thực. */
    fun encryptCipher(username: String): Cipher {
        val key = getOrCreateKey(username)
        return Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
    }

    /** Gọi trong onAuthenticationSucceeded — cipher đã unlock, mã hóa và lưu token. */
    fun saveToken(username: String, cipher: Cipher, refreshToken: String) {
        val ciphertext = cipher.doFinal(refreshToken.toByteArray(Charsets.UTF_8))
        prefs.edit {
            putString(ciphertextKey(username), Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            putString(ivKey(username), Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
        }
        setLastUsername(username)
    }

    /**
     * Cipher ở chế độ DECRYPT dùng đúng IV đã lưu, sẵn sàng bọc trong CryptoObject.
     * Throws KeyPermanentlyInvalidatedException nếu user đã thêm/xoá vân tay mới trên máy
     * (Android tự huỷ key Keystore khi danh sách sinh trắc học đổi) — cần bắt và yêu cầu
     * đăng nhập lại bằng password.
     */
    fun decryptCipher(username: String): Cipher {
        val key = getOrCreateKey(username)
        val iv = Base64.decode(prefs.getString(ivKey(username), null), Base64.NO_WRAP)
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        }
    }

    /** Gọi trong onAuthenticationSucceeded — cipher đã unlock, giải mã trả về refresh_token. */
    fun readToken(username: String, cipher: Cipher): String {
        val ciphertext = Base64.decode(prefs.getString(ciphertextKey(username), null), Base64.NO_WRAP)
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun getOrCreateKey(username: String): SecretKey {
        val alias = keyAlias(username)
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true) // bắt buộc BiometricPrompt mới unlock được key
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    // Namespace mọi khoá SharedPreferences/Keystore theo username để nhiều tài khoản không
    // ghi đè lên nhau trên cùng thiết bị.
    private fun keyAlias(username: String) = "$KEY_ALIAS_PREFIX$username"
    private fun ciphertextKey(username: String) = "$KEY_CIPHERTEXT_PREFIX$username"
    private fun ivKey(username: String) = "$KEY_IV_PREFIX$username"
    private fun enabledKey(username: String) = "$KEY_ENABLED_PREFIX$username"

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val KEY_LAST_USERNAME = "last_username"
        private const val KEY_ALIAS_PREFIX = "biometric_refresh_token_key_"
        private const val KEY_CIPHERTEXT_PREFIX = "refresh_token_ciphertext_"
        private const val KEY_IV_PREFIX = "refresh_token_iv_"
        private const val KEY_ENABLED_PREFIX = "biometric_enabled_"
    }
}

/** true nếu key Keystore đã bị Android huỷ do sinh trắc học trên máy thay đổi. */
fun Throwable.isKeyInvalidated(): Boolean =
    (this is KeyPermanentlyInvalidatedException) || (cause is KeyPermanentlyInvalidatedException)

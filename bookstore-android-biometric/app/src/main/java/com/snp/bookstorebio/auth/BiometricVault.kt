package com.snp.bookstorebio.auth

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
 */
class BiometricVault(context: Context) {
    private val prefs = context.getSharedPreferences("biometric_vault", Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    fun hasStoredToken(): Boolean = prefs.contains(KEY_CIPHERTEXT)

    fun clear() {
        prefs.edit { clear() }
        runCatching { keyStore.deleteEntry(KEY_ALIAS) }
    }

    /** Cipher ở chế độ ENCRYPT, sẵn sàng bọc trong BiometricPrompt.CryptoObject để xác thực. */
    fun encryptCipher(): Cipher {
        val key = getOrCreateKey()
        return Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
    }

    /** Gọi trong onAuthenticationSucceeded — cipher đã unlock, mã hóa và lưu token. */
    fun saveToken(cipher: Cipher, refreshToken: String) {
        val ciphertext = cipher.doFinal(refreshToken.toByteArray(Charsets.UTF_8))
        prefs.edit {
            putString(KEY_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
        }
    }

    /**
     * Cipher ở chế độ DECRYPT dùng đúng IV đã lưu, sẵn sàng bọc trong CryptoObject.
     * Throws KeyPermanentlyInvalidatedException nếu user đã thêm/xoá vân tay mới trên máy
     * (Android tự huỷ key Keystore khi danh sách sinh trắc học đổi) — cần bắt và yêu cầu
     * đăng nhập lại bằng password.
     */
    fun decryptCipher(): Cipher {
        val key = getOrCreateKey()
        val iv = Base64.decode(prefs.getString(KEY_IV, null), Base64.NO_WRAP)
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        }
    }

    /** Gọi trong onAuthenticationSucceeded — cipher đã unlock, giải mã trả về refresh_token. */
    fun readToken(cipher: Cipher): String {
        val ciphertext = Base64.decode(prefs.getString(KEY_CIPHERTEXT, null), Base64.NO_WRAP)
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true) // bắt buộc BiometricPrompt mới unlock được key
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "biometric_refresh_token_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val KEY_CIPHERTEXT = "refresh_token_ciphertext"
        private const val KEY_IV = "refresh_token_iv"
    }
}

/** true nếu key Keystore đã bị Android huỷ do sinh trắc học trên máy thay đổi. */
fun Throwable.isKeyInvalidated(): Boolean =
    this is KeyPermanentlyInvalidatedException || cause is KeyPermanentlyInvalidatedException

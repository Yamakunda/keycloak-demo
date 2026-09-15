# 6. Hướng B — Local Biometric App-Lock (`bookstore-android-biometric`)

## 6.1 Nguyên lý

Mô hình của app ngân hàng: **đăng nhập bằng mật khẩu đúng một lần**, sau đó app mã hoá `refresh_token` bằng khoá AES-256/GCM nằm trong Android Keystore với thuộc tính `setUserAuthenticationRequired(true)`.

Các lần mở app sau, `BiometricPrompt` mở khoá key, giải mã `refresh_token`, gọi thẳng token endpoint để lấy `access_token` mới — **không mở lại Custom Tab, không nhập lại mật khẩu**.

Keycloak hoàn toàn không biết gì về vân tay: nó chỉ thấy một `refresh_token` grant bình thường. Toàn bộ phần sinh trắc học nằm ở client, không cần bất kỳ extension/SPI nào phía IdP.

Ranh giới tin cậy:

| Thành phần | Giữ gì | Ai bảo vệ |
|---|---|---|
| Android Keystore (TEE/StrongBox) | AES key, không bao giờ export ra userspace | Phần cứng + OS |
| SharedPreferences `biometric_vault` | ciphertext + IV (vô nghĩa nếu không có key) | App sandbox |
| Keycloak | offline session tương ứng refresh_token | Server |

Kể cả khi root và đọc được SharedPreferences, kẻ tấn công chỉ lấy được ciphertext; muốn giải mã phải qua đúng `BiometricPrompt` trên chính thiết bị đó.

---

## 6.2 Cấu hình Keycloak — client `biometric-demo`

Trích `keycloak-config/test-realm.json`:

```json
{
  "clientId": "biometric-demo",
  "publicClient": true,
  "standardFlowEnabled": true,
  "directAccessGrantsEnabled": false,
  "redirectUris": ["com.snp.bookstorebio:/oauth2redirect"],
  "webOrigins": [],
  "defaultClientScopes": ["web-origins","acr","profile","roles","basic","email","offline_access"],
  "attributes": {
    "pkce.code.challenge.method": "S256",
    "use.refresh.tokens": "true",
    "post.logout.redirect.uris": "+",
    "client.offline.session.max.lifespan": "2592000",
    "client.offline.session.idle.timeout": "2592000"
  }
}
```

| Cấu hình | Vai trò |
|---|---|
| `publicClient: true` | App mobile không giữ được `client_secret` → dùng PKCE thay thế |
| `directAccessGrantsEnabled: false` | Cấm ROPC — app không bao giờ chạm vào mật khẩu người dùng |
| `pkce.code.challenge.method: S256` | Bắt buộc PKCE, chống chặn authorization code trên deep link |
| `offline_access` trong `defaultClientScopes` | **Bắt buộc** — nếu không, refresh token chết theo SSO session (vài phút) |
| `use.refresh.tokens: true` | Bật cấp refresh token cho client này |
| `client.offline.session.max.lifespan` = 2592000 | Offline session sống tối đa 30 ngày |
| `client.offline.session.idle.timeout` = 2592000 | Không dùng trong 30 ngày thì hết hạn |
| `redirectUris` = scheme riêng của app | Khớp `manifestPlaceholders["appAuthRedirectScheme"]` |

Realm-level (cùng file):

```json
"revokeRefreshToken": false,
"offlineSessionIdleTimeout": 2592000,
"offlineSessionMaxLifespan": 5184000,
"offlineSessionMaxLifespanEnabled": false
```

`revokeRefreshToken: false` → Keycloak **không rotate** refresh_token mỗi lần dùng, nên token đã mã hoá trong vault tái sử dụng được nhiều lần mà không phải re-encrypt sau mỗi lần mở app. Đây là quyết định có chủ đích để đơn giản hoá PoC; phần 9 phân tích đánh đổi bảo mật của nó.

---

## 6.3 Cấu trúc mã nguồn

App theo Clean Architecture 3 lớp + Hilt DI, tổng ~950 dòng Kotlin:

```
com.snp.bookstorebio/
├── BookstoreApplication.kt              @HiltAndroidApp
├── data/
│   ├── source/local/BiometricVault.kt   Keystore + mã hoá refresh_token  (130)
│   ├── source/remote/AuthManager.kt     Bọc AppAuth: login / refresh / logout (136)
│   ├── source/remote/BookstoreApi.kt    OkHttp + Bearer token
│   └── repository/BookRepositoryImpl.kt
├── domain/                              Book, BookRepository, GetBooksUseCase
└── presentation/
    ├── di/AppModule.kt                  @Singleton AuthManager, BiometricVault, BookstoreApi
    ├── viewmodel/AppViewModel.kt        UiState + điều phối vault ↔ token (155)
    └── ui/MainActivity.kt               FragmentActivity + BiometricPrompt + Compose (343)
```

Ba `@Singleton` trong `AppModule` là điểm neo quan trọng: `BiometricVault` và `AuthManager` phải sống cùng vòng đời process để `AuthState` trong ViewModel không bị mất khi Activity recreate.

### Cấu hình build — `app/build.gradle.kts`

```kotlin
defaultConfig {
    applicationId = "com.snp.bookstorebio"
    minSdk = 28   // CryptoObject + Keystore user-auth-bound key ổn định nhất từ API 28+
    manifestPlaceholders["appAuthRedirectScheme"] = "com.snp.bookstorebio"

    buildConfigField("String", "KEYCLOAK_BASE_URL",
        "\"${project.findProperty("kcBaseUrl") ?: "https://<ngrok>.ngrok-free.dev"}\"")
    buildConfigField("String", "KEYCLOAK_REALM",     "\"test\"")
    buildConfigField("String", "KEYCLOAK_CLIENT_ID", "\"biometric-demo\"")
    buildConfigField("String", "BOOKSTORE_API_BASE_URL",
        "\"${project.findProperty("apiBaseUrl") ?: "http://192.168.x.x:3043"}\"")
}
```

Dependency cốt lõi:

| Thư viện | Version | Dùng để |
|---|---|---|
| `net.openid:appauth` | 0.11.1 | Authorization Code + PKCE, token endpoint |
| `androidx.browser:browser` | 1.8.0 | Custom Tabs cho lần login đầu |
| `androidx.biometric:biometric` | 1.2.0-alpha05 | `BiometricPrompt` + `CryptoObject` |
| `com.google.dagger:hilt-android` | 2.51.1 | DI |
| `com.squareup.okhttp3:okhttp` | 4.12.0 | Gọi `bookstore-api-mobile` |

Endpoint tách làm hai nguồn khác nhau và **đây là chi tiết dễ nhầm nhất**: `KEYCLOAK_BASE_URL` trỏ ngrok HTTPS (Custom Tab và AppAuth đều khắt khe với scheme), còn `BOOKSTORE_API_BASE_URL` trỏ IP LAN qua HTTP.

### `AndroidManifest.xml`

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.USE_BIOMETRIC" />

<application android:usesCleartextTraffic="true" ...>
    <activity android:name=".presentation.ui.MainActivity"
              android:exported="true"
              android:launchMode="singleTask">   <!-- tránh tạo instance mới khi deep link về -->
        ...
    </activity>

    <!-- AppAuth bắt redirect từ Custom Tab -->
    <activity android:name="net.openid.appauth.RedirectUriReceiverActivity"
              android:exported="true">
        <intent-filter>
            <action android:name="android.intent.action.VIEW" />
            <category android:name="android.intent.category.DEFAULT" />
            <category android:name="android.intent.category.BROWSABLE" />
            <data android:scheme="com.snp.bookstorebio" android:path="/oauth2redirect" />
        </intent-filter>
    </activity>
</application>
```

---

## 6.4 Luồng lần đăng nhập đầu tiên

```
User bấm "Đăng nhập"
   └─ AuthManager.buildLoginIntent()  ──► Custom Tab ──► Keycloak (username/password)
                                                              │
        RedirectUriReceiverActivity ◄── com.snp.bookstorebio:/oauth2redirect
                     │
        handleAuthorizationResponse() → performTokenRequest(code + PKCE verifier)
                     │
        AppViewModel.onFirstLoginResult(AuthState)  → UiState.LoggedIn
                     │
        startBiometricEnrollmentFlow() → canAuthenticate(BIOMETRIC_STRONG)
                     │
        promptSaveToVault() → BiometricPrompt(ENCRYPT cipher)
                     │
        onAuthenticationSucceeded → vault.saveToken(username, cipher, refreshToken)
```

## 6.5 Luồng mở app các lần sau

```
App khởi động → AppViewModel.init
   └─ usernameWithUsableVault(): lastUsername + isBiometricEnabled + hasStoredToken
        │  (null → màn hình đăng nhập thường)
        ▼
   UiState.LoggedOut(savedUsername = "alice")
   User bấm "Đăng nhập bằng vân tay cho alice"
        │
   vault.decryptCipher("alice")     ← có thể ném KeyPermanentlyInvalidatedException
        │
   BiometricPrompt(DECRYPT cipher) → onAuthenticationSucceeded
        │
   vault.readToken() → refresh_token (plaintext, chỉ trong RAM)
        │
   AuthManager.exchangeRefreshToken() → POST /token  (KHÔNG mở Custom Tab)
        │
   AuthState mới → UiState.LoggedIn → loadBooks() với Bearer access_token
```

Toàn bộ đường đi này không chạm tới trình duyệt và không cần Keycloak biết gì về sinh trắc học.

---

## 6.6 Hiện thực `BiometricVault.kt`

### Khoá AES gắn Keystore

Key chỉ dùng được sau khi xác thực sinh trắc học thành công trong cùng phiên `BiometricPrompt`:

```kotlin
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
        .setUserAuthenticationRequired(true)   // ← điểm mấu chốt
        .build()
    generator.init(spec)
    return generator.generateKey()
}
```

`setUserAuthenticationRequired(true)` khiến mọi thao tác `Cipher.doFinal()` bị Keystore từ chối trừ khi cipher đó đã đi qua một `CryptoObject` được `BiometricPrompt` unlock. Không có "đường vòng" ở tầng ứng dụng.

### Namespace theo username

Mọi khoá SharedPreferences và key alias đều gắn hậu tố username:

```kotlin
private fun keyAlias(username: String)      = "biometric_refresh_token_key_$username"
private fun ciphertextKey(username: String) = "refresh_token_ciphertext_$username"
private fun ivKey(username: String)         = "refresh_token_iv_$username"
private fun enabledKey(username: String)    = "biometric_enabled_$username"
```

Nhờ vậy nhiều tài khoản cùng bật vân tay song song trên một thiết bị mà không ghi đè nhau, và `clear(username)` chỉ xoá đúng một vault. `lastUsername` được lưu riêng để lúc mở app biết gợi ý tài khoản nào.

### Mã hoá / giải mã

```kotlin
/** Cipher ENCRYPT, sẵn sàng bọc trong CryptoObject. */
fun encryptCipher(username: String): Cipher =
    Cipher.getInstance("AES/GCM/NoPadding")
        .apply { init(Cipher.ENCRYPT_MODE, getOrCreateKey(username)) }

fun saveToken(username: String, cipher: Cipher, refreshToken: String) {
    val ciphertext = cipher.doFinal(refreshToken.toByteArray(Charsets.UTF_8))
    prefs.edit {
        putString(ciphertextKey(username), Base64.encodeToString(ciphertext, Base64.NO_WRAP))
        putString(ivKey(username),         Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
    }
    setLastUsername(username)
}

fun decryptCipher(username: String): Cipher {
    val iv = Base64.decode(prefs.getString(ivKey(username), null), Base64.NO_WRAP)
    return Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(Cipher.DECRYPT_MODE, getOrCreateKey(username), GCMParameterSpec(128, iv))
    }
}

fun readToken(username: String, cipher: Cipher): String {
    val ct = Base64.decode(prefs.getString(ciphertextKey(username), null), Base64.NO_WRAP)
    return String(cipher.doFinal(ct), Charsets.UTF_8)
}
```

GCM sinh IV ngẫu nhiên mỗi lần encrypt nên **phải lưu IV cùng ciphertext**; thiếu IV thì không dựng lại được decrypt cipher.

### Xử lý key bị OS huỷ

Khi user thêm/xoá vân tay trên máy, Android **huỷ vĩnh viễn** mọi key `setUserAuthenticationRequired`:

```kotlin
fun Throwable.isKeyInvalidated(): Boolean =
    (this is KeyPermanentlyInvalidatedException) || (cause is KeyPermanentlyInvalidatedException)
```

Phải bắt cả `cause` vì exception thường bị bọc lại khi ném ra từ `Cipher.init()`.

---

## 6.7 Hiện thực `MainActivity.kt` — BiometricPrompt

`MainActivity` kế thừa `FragmentActivity` (yêu cầu bắt buộc của `androidx.biometric` — `ComponentActivity` không đủ vì prompt cần FragmentManager).

### Mở khoá vault

```kotlin
private fun promptUnlockVault(username: String) {
    val cipher = try {
        viewModel.biometricVault.decryptCipher(username)
    } catch (e: Exception) {
        if (e.isKeyInvalidated()) viewModel.onVaultInvalidated(username)
        return
    }
    val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val authedCipher = result.cryptoObject?.cipher ?: return
                val refreshToken = viewModel.biometricVault.readToken(username, authedCipher)
                viewModel.onBiometricUnlocked(username, refreshToken)
            }
        })
    prompt.authenticate(
        biometricPromptInfo("Mở khoá Bookstore", "Xác thực vân tay để đăng nhập"),
        BiometricPrompt.CryptoObject(cipher),
    )
}
```

Điểm cần chú ý: **phải dùng `result.cryptoObject.cipher`**, không dùng lại biến `cipher` ban đầu — chỉ instance do Keystore trả về sau xác thực mới ở trạng thái đã unlock.

### Lưu token vào vault

```kotlin
private fun promptSaveToVault() {
    val refreshToken = viewModel.pendingRefreshTokenToSave()
    val username = viewModel.currentUsername()
    if (refreshToken == null || username == null) { viewModel.onBiometricDisabled(); return }

    val cipher = viewModel.biometricVault.encryptCipher(username)
    val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                viewModel.biometricVault.saveToken(
                    username, result.cryptoObject!!.cipher!!, refreshToken)
                viewModel.onSavedToVault()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                viewModel.onBiometricDisabled()   // user huỷ → không bật tính năng nửa vời
            }
        })
    prompt.authenticate(
        biometricPromptInfo("Lưu đăng nhập", "Xác thực vân tay để lần sau mở app không cần mật khẩu"),
        BiometricPrompt.CryptoObject(cipher),
    )
}
```

### Kiểm tra khả dụng trước khi tạo key

Đây là bug thực tế gặp phải trong PoC: gọi `encryptCipher()` (tức `getOrCreateKey`) trên máy chưa đăng ký sinh trắc học sẽ ném `InvalidAlgorithmParameterException` ngay ở `generator.init(spec)`, **trước cả khi** prompt kịp hiện. Vì vậy mọi lối vào đều đi qua `startBiometricEnrollmentFlow()`:

```kotlin
private fun startBiometricEnrollmentFlow() {
    val username = viewModel.currentUsername() ?: return
    when (BiometricManager.from(this).canAuthenticate(BIOMETRIC_STRONG)) {
        BiometricManager.BIOMETRIC_SUCCESS -> {
            viewModel.biometricVault.setBiometricEnabled(username, true)
            promptSaveToVault()
        }
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
            viewModel.onBiometricUnavailable("Thiết bị chưa đăng ký vân tay/khuôn mặt/PIN…")
            awaitingBiometricEnrollment = true          // onResume() sẽ thử lại
            startActivity(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
                        putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, BIOMETRIC_STRONG)
                    }
                else Intent(Settings.ACTION_SECURITY_SETTINGS)
            )
        }
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
            viewModel.onBiometricUnavailable("Thiết bị không hỗ trợ xác thực sinh trắc học.")
        else -> viewModel.onBiometricUnavailable("Không thể bật đăng nhập bằng vân tay lúc này.")
    }
}

override fun onResume() {
    super.onResume()
    if (awaitingBiometricEnrollment) {
        awaitingBiometricEnrollment = false
        startBiometricEnrollmentFlow()    // user vừa quay lại từ Settings
    }
}
```

Cờ `awaitingBiometricEnrollment` + `onResume()` là cách nối lại luồng sau khi user rời app sang Settings đăng ký vân tay rồi quay về — không cần `ActivityResultLauncher` vì màn hình enrollment không trả result đáng tin cậy trên mọi OEM.

`BIOMETRIC_STRONG` (Class 3) là bắt buộc: chỉ authenticator Class 3 mới được phép unlock key Keystore. `DEVICE_CREDENTIAL` hay `BIOMETRIC_WEAK` sẽ không dùng được với `CryptoObject`.

---

## 6.8 Điều phối trạng thái — `AppViewModel.kt`

State machine gọn ba trạng thái:

```kotlin
sealed interface UiState {
    data class LoggedOut(val savedUsername: String? = null) : UiState
    data object LoggingIn : UiState
    data class LoggedIn(
        val username: String,
        val books: List<Book> = emptyList(),
        val loadingBooks: Boolean = false,
        val error: String? = null,
        val biometricEnabled: Boolean = false,
    ) : UiState
}
```

`savedUsername` khác null chính là tín hiệu để màn hình đăng nhập hiện thêm nút "Đăng nhập bằng vân tay cho …". Nó chỉ có giá trị khi **cả ba** điều kiện đúng:

```kotlin
private fun usernameWithUsableVault(): String? {
    val lastUsername = biometricVault.lastUsername() ?: return null
    return lastUsername.takeIf {
        biometricVault.isBiometricEnabled(it) && biometricVault.hasStoredToken(it)
    }
}
```

Đổi tài khoản thì vault của tài khoản trước bị xoá ngay tại thời điểm login thành công:

```kotlin
fun onFirstLoginResult(state: AuthState?) {
    ...
    val username = state.idToken?.let { decodePreferredUsername(it) } ?: "user"
    val previousUsername = biometricVault.lastUsername()
    if (previousUsername != null && previousUsername != username) {
        biometricVault.clear(previousUsername)
    }
    ...
}
```

Username lấy từ claim `preferred_username` trong `id_token`, decode thủ công bằng Base64 URL-safe — PoC không kéo thêm thư viện JWT vì không cần verify chữ ký ở client (token đã được backend verify).

Khi refresh token chết, app không giữ trạng thái mồ côi:

```kotlin
fun onBiometricUnlocked(username: String, refreshToken: String) {
    authManager.exchangeRefreshToken(refreshToken) { tokenResponse, exception ->
        if (tokenResponse == null) {
            biometricVault.clear(username)          // token hết hạn/bị revoke
            _uiState.value = UiState.LoggedOut()
            return@exchangeRefreshToken
        }
        authState = AuthState().apply { update(tokenResponse, exception) }
        _uiState.value = UiState.LoggedIn(username = ..., biometricEnabled = true)
        loadBooks()
    }
}
```

---

## 6.9 Đổi refresh token — `AuthManager.kt`

```kotlin
fun exchangeRefreshToken(
    refreshToken: String,
    onResult: (TokenResponse?, AuthorizationException?) -> Unit,
) {
    val request = TokenRequest.Builder(serviceConfig, BuildConfig.KEYCLOAK_CLIENT_ID)
        .setGrantType("refresh_token")
        .setRefreshToken(refreshToken)
        .setScopes("openid profile email offline_access")
        .build()
    service.performTokenRequest(request, onResult)
}
```

Lần đăng nhập đầu xin đúng scope đó để Keycloak cấp offline token:

```kotlin
AuthorizationRequest.Builder(serviceConfig, CLIENT_ID, ResponseTypeValues.CODE, redirectUri)
    .setScope("openid profile email offline_access")
    .build()
```

Thiếu `offline_access` là lỗi âm thầm khó chịu nhất: app chạy đúng ngay sau khi login, nhưng mở lại sau vài phút thì refresh token đã chết theo SSO session và user bị đá về màn hình đăng nhập — trông y hệt lỗi vân tay.

`AppAuthConfiguration` bị ghi đè `ConnectionBuilder` để cho phép HTTP cleartext, vì AppAuth chặn cứng mọi kết nối không HTTPS ở bước đổi token bất kể `usesCleartextTraffic`:

```kotlin
private object CleartextConnectionBuilder : ConnectionBuilder {
    override fun openConnection(uri: Uri): HttpURLConnection =
        (URL(uri.toString()).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            instanceFollowRedirects = false
        }
}
// KHÔNG dùng cho build release trỏ backend thật ngoài LAN.
```

---

## 6.10 Gọi API nghiệp vụ

Sau khi có `access_token`, phần còn lại là OAuth2 Bearer thuần tuý — backend `bookstore-api-mobile` không biết và không cần biết token đến từ vân tay hay từ mật khẩu:

```kotlin
val request = Request.Builder()
    .url("${BuildConfig.BOOKSTORE_API_BASE_URL}/api/books")
    .header("Authorization", "Bearer $accessToken")
    .build()
```

Đây là điểm chứng minh tính không xâm lấn của hướng B: **không một dòng code backend nào phải thay đổi**.

---

## 6.11 Quy tắc vòng đời vault

Các quyết định thiết kế dưới đây được đưa ra để khớp với hành vi người dùng thực tế:

| Hành động | Ảnh hưởng tới vault | Hiện thực | Lý do |
|---|---|---|---|
| Đăng xuất | Không xoá | `onLoggedOut()` chỉ reset `authState` | Giống app ngân hàng: đăng nhập lại đúng tài khoản vẫn dùng vân tay ngay |
| Tắt toggle "Đăng nhập bằng vân tay" | Xoá key Keystore + ciphertext | `onBiometricDisabled()` → `vault.clear()` | Tương đương "quên thiết bị này"; không bắt xác thực vân tay để tắt |
| Đăng nhập tài khoản khác | Tự xoá vault của tài khoản trước | `onFirstLoginResult()` so `lastUsername` | Máy chỉ giữ vân tay cho tài khoản gần nhất — tránh nhầm lẫn danh tính |
| Thêm/xoá vân tay trên máy | Android tự huỷ key → app xoá vault | `isKeyInvalidated()` → `onVaultInvalidated()` | Hành vi bảo mật mặc định của OS, không phải lỗi |
| User huỷ prompt khi đang lưu | Không bật tính năng | `onAuthenticationError` → `onBiometricDisabled()` | Không để trạng thái "đã bật nhưng chưa có token" |
| refresh_token hết hạn/bị revoke | Xoá vault, về màn đăng nhập | `onBiometricUnlocked()` nhánh null | Không giữ trạng thái mồ côi |

---

## 6.12 Những rào cản đã gặp và cách xử lý

| Vấn đề | Triệu chứng | Cách xử lý |
|---|---|---|
| `androidx.biometric` cần FragmentManager | Crash khi tạo `BiometricPrompt` | `MainActivity : FragmentActivity` thay vì `ComponentActivity` |
| Tạo key khi chưa enroll vân tay | `InvalidAlgorithmParameterException` | `canAuthenticate(BIOMETRIC_STRONG)` **trước** khi chạm tới `getOrCreateKey` |
| User rời app sang Settings để enroll | Luồng đứt giữa chừng | Cờ `awaitingBiometricEnrollment` + retry trong `onResume()` |
| AppAuth chặn HTTP | Đổi token fail dù Manifest đã bật cleartext | `CleartextConnectionBuilder` (chỉ cho debug) |
| Thiếu `offline_access` | Refresh fail sau vài phút, trông như lỗi vân tay | Thêm scope ở cả login lẫn refresh + `client.offline.session.*` |
| Thay đổi vân tay hệ thống | `KeyPermanentlyInvalidatedException` bị bọc trong `cause` | Kiểm tra cả `this` lẫn `cause` trong `isKeyInvalidated()` |
| Nhiều tài khoản trên một máy | Vault ghi đè lẫn nhau | Namespace mọi khoá theo `username` |

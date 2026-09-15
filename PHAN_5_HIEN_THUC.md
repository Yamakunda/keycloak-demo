# 5. Hướng A — WebAuthn Passwordless (`bookstore-android`)

## 5.1 Nguyên lý

WebAuthn (chuẩn W3C) cho phép thiết bị sinh một **cặp khoá public/private** gắn với Relying Party (Keycloak) và chính thiết bị đó:

- **Private key** không bao giờ rời thiết bị, nằm trong secure storage (TEE/StrongBox), mở khoá bằng sinh trắc học.
- **Public key** gửi lên Keycloak lúc đăng ký, dùng để xác minh chữ ký ở các lần sau.

Không có mật khẩu nào tồn tại → không có gì để phishing, để rò rỉ từ DB, hay để brute-force.

Điểm cốt lõi về mặt kiến trúc client: **app Android không hề "biết" WebAuthn**. Toàn bộ nghi thức `navigator.credentials.create()` / `.get()` diễn ra bên trong Chrome Custom Tab, giữa trình duyệt và Keycloak. App chỉ mở Custom Tab và nhận lại authorization code như một luồng OAuth2 thông thường.

```
App Android          Chrome Custom Tab           Keycloak            Android Credential Manager
    │                        │                       │                          │
    ├─ buildLoginIntent() ──►│                       │                          │
    │                        ├─ GET /auth?...  ─────►│                          │
    │                        │◄──── Browser Passwordless flow ────┤             │
    │                        ├─ navigator.credentials.get() ──────────────────►│
    │                        │                       │        (vân tay/Face/PIN)│
    │                        │◄──── assertion + chữ ký ───────────────────────┤
    │                        ├─ POST assertion ─────►│ verify bằng public key   │
    │◄─ deep link + code ────┤◄──── 302 redirect ────┤                          │
    ├─ performTokenRequest(code + PKCE) ────────────►│                          │
    │◄─ access/id/refresh token ─────────────────────┤                          │
```

Hệ quả trực tiếp: nếu Keycloak sau này đổi cách xác thực (thêm passkey mới, đổi policy, bật MFA), **app không phải build lại**.

---

## 5.2 Cấu hình Keycloak

### a. Realm WebAuthn Passwordless Policy

Keycloak tách làm hai nhóm policy độc lập: `webAuthnPolicy*` (WebAuthn dùng như yếu tố thứ hai) và `webAuthnPolicyPasswordless*` (WebAuthn thay thế hoàn toàn mật khẩu). PoC chỉ siết nhóm thứ hai; nhóm đầu để nguyên mặc định `"not specified"`.

Trích `keycloak-config/test-realm.json`:

```json
"webAuthnPolicyPasswordlessRpEntityName": "keycloak",
"webAuthnPolicyPasswordlessSignatureAlgorithms": ["ES256", "RS256"],
"webAuthnPolicyPasswordlessRpId": "",
"webAuthnPolicyPasswordlessAttestationConveyancePreference": "not specified",
"webAuthnPolicyPasswordlessAuthenticatorAttachment": "platform",
"webAuthnPolicyPasswordlessRequireResidentKey": "Yes",
"webAuthnPolicyPasswordlessUserVerificationRequirement": "required",
"webAuthnPolicyPasswordlessCreateTimeout": 0,
"webAuthnPolicyPasswordlessAvoidSameAuthenticatorRegister": false,
"webAuthnPolicyPasswordlessAcceptableAaguids": [],
"webAuthnPolicyPasswordlessExtraOrigins": []
```

| Tham số | Giá trị | Vì sao |
|---|---|---|
| `AuthenticatorAttachment` | `platform` | Chỉ chấp nhận authenticator gắn liền thiết bị (vân tay/Face/PIN), loại trừ security key rời USB/NFC |
| `RequireResidentKey` | `Yes` | **Bắt buộc** — ép credential là discoverable để đăng nhập *usernameless*, không phải nhập username trước |
| `UserVerificationRequirement` | `required` | Bắt buộc xác thực sinh trắc học thật, không chấp nhận chỉ "user presence" (chạm) |
| `RpId` | `""` (rỗng) | Keycloak tự suy từ hostname request — xem 5.6, đây là chỗ dễ vỡ nhất khi đổi domain |
| `SignatureAlgorithms` | `ES256, RS256` | ES256 là thuật toán mặc định của Android Credential Manager |
| `AttestationConveyancePreference` | `not specified` | Không yêu cầu attestation → không cần whitelist AAGUID, giảm ma sát cho PoC |
| `AcceptableAaguids` | `[]` | Chấp nhận mọi loại authenticator platform |

Ba tham số in đậm ý nghĩa nhất là bộ ba `platform` + `RequireResidentKey: Yes` + `UserVerification: required` — đúng định nghĩa của một passkey theo FIDO2. Bỏ `RequireResidentKey` thì luồng tụt về "nhập username rồi mới quét vân tay", mất phần lớn giá trị trải nghiệm.

### b. Client `passwordless-demo`

```json
{
  "id": "d3f8a1b2-4c5d-4e6f-8a9b-0c1d2e3f4a5b",
  "clientId": "passwordless-demo",
  "name": "Passwordless WebAuthn Demo",
  "publicClient": true,
  "standardFlowEnabled": true,
  "implicitFlowEnabled": false,
  "directAccessGrantsEnabled": false,
  "redirectUris": ["http://localhost:3042/*", "com.snp.bookstore:/oauth2redirect"],
  "webOrigins": ["http://localhost:3042"],
  "defaultClientScopes": ["web-origins","acr","profile","roles","basic","email"],
  "attributes": {
    "pkce.code.challenge.method": "S256",
    "post.logout.redirect.uris": "+"
  },
  "authenticationFlowBindingOverrides": {
    "browser": "b8f2c1a0-3d4e-4f5a-9b6c-7d8e9f0a1b2c"
  }
}
```

| Cấu hình | Vai trò |
|---|---|
| `publicClient: true` | Không có `client_secret` — đúng chuẩn cho mobile, vì secret nhúng trong APK thì decompile là đọc được |
| `pkce.code.challenge.method: S256` | Bắt buộc PKCE, chống chặn authorization code trên deep link |
| `directAccessGrantsEnabled: false` | Cấm ROPC — nếu để `true` thì vẫn còn đường lấy token bằng username/password, phá vỡ mục tiêu passwordless |
| `implicitFlowEnabled: false` | Không dùng implicit flow (đã deprecated trong OAuth 2.1) |
| `redirectUris` có 2 mục | `localhost:3042` cho web demo, `com.snp.bookstore:/oauth2redirect` cho app Android — cùng một client phục vụ cả hai |
| `authenticationFlowBindingOverrides.browser` | Trỏ tới flow tuỳ chỉnh "Browser Passwordless" |

**`authenticationFlowBindingOverrides` là quyết định thiết kế quan trọng nhất của phần cấu hình này.** Nó gắn flow passwordless vào *riêng client này*, thay vì đổi browser flow mặc định của cả realm. Nhờ vậy các client khác đang chạy trong realm `test` (`bookstore-fe`, `bookstore-api`, `razor-demo`…) hoàn toàn không bị ảnh hưởng — đây chính là lý do PoC có thể chạy song song trên hệ thống thật mà không cần cửa sổ downtime.

Đáng chú ý: `defaultClientScopes` của client này **không có `offline_access`** — khác hẳn `biometric-demo` ở hướng B. Hướng A không cần refresh token sống lâu vì mỗi lần mở app đều xác thực lại bằng passkey, vốn đã gần như không có ma sát.

### c. Authentication Flow "Browser Passwordless"

```json
{
  "id": "b8f2c1a0-3d4e-4f5a-9b6c-7d8e9f0a1b2c",
  "alias": "Browser Passwordless",
  "description": "Passwordless login via WebAuthn passkey (Face ID / vân tay / PIN), fallback về SSO cookie nếu đã có session.",
  "providerId": "basic-flow",
  "topLevel": true,
  "builtIn": false,
  "authenticationExecutions": [
    { "authenticator": "auth-cookie",                         "requirement": "ALTERNATIVE", "priority": 10 },
    { "authenticator": "webauthn-authenticator-passwordless",  "requirement": "ALTERNATIVE", "priority": 20 },
    { "authenticator": "auth-username-password-form",          "requirement": "ALTERNATIVE", "priority": 30 }
  ]
}
```

Ba execution đều `ALTERNATIVE` → chỉ cần **một trong ba** thành công, xét theo thứ tự `priority`:

1. **`auth-cookie`** — đã có SSO session (cookie `KEYCLOAK_IDENTITY` còn hạn) thì vào thẳng, không hỏi gì. Đây là lý do lần đăng nhập thứ hai trong cùng phiên trình duyệt nhanh gần như tức thì.
2. **`webauthn-authenticator-passwordless`** — hiện nút "Sign in with passkey", gọi `navigator.credentials.get()`. Vì policy đặt `RequireResidentKey: Yes`, Keycloak gửi `allowCredentials: []` rỗng và trình duyệt tự hỏi Credential Manager xem có passkey nào cho RP này — **không cần biết username trước**.
3. **`auth-username-password-form`** — fallback bằng mật khẩu. **Bắt buộc phải giữ**: user chưa có credential nào thì không thể tạo passkey, vì muốn đăng ký passkey phải xác thực được danh tính bằng cách khác trước. Đây là bài toán "con gà quả trứng" cố hữu của mọi hệ thống passwordless.

Đánh đổi cần nói rõ: chừng nào nhánh 3 còn tồn tại thì hệ thống chưa thực sự "không mật khẩu" — bề mặt tấn công phishing vẫn còn. Trong triển khai thật, nhánh này nên được thay bằng kênh bootstrap khác (magic link qua email, OTP một lần, hoặc quy trình cấp tại quầy) và tắt hẳn form mật khẩu.

### d. Required Action và user demo

```json
{
  "username": "passkey-demo",
  "enabled": true,
  "email": "passkey-demo@gmail.com",
  "firstName": "Passkey",
  "requiredActions": ["webauthn-register-passwordless"],
  "credentials": [{ "type": "password", "value": "123456" }],
  "realmRoles": ["default-roles-test"]
}
```

Required action `webauthn-register-passwordless` (đã bật sẵn ở realm-level, `priority: 80`) buộc user đăng ký passkey **ngay sau lần đăng nhập bằng mật khẩu đầu tiên** — người dùng không có cơ hội bỏ qua bước này.

Realm còn một user `test` (roles `book-admin`, `default-roles-test`) không gắn required action, dùng để đối chứng luồng đăng nhập mật khẩu thông thường vẫn hoạt động bình thường trên cùng realm.

---

## 5.3 Cấu trúc mã nguồn app

App theo Clean Architecture 3 lớp + Hilt DI, tổng ~600 dòng Kotlin — **ít hơn hướng B khoảng 350 dòng**, vì không phải quản lý Keystore, cipher, hay vòng đời vault:

```
com.snp.bookstore/
├── BookstoreApplication.kt              @HiltAndroidApp
├── data/
│   ├── source/local/TokenStore.kt       Lưu AuthState vào SharedPreferences  (30)
│   ├── source/remote/AuthManager.kt     Bọc AppAuth: login / logout          (86)
│   ├── source/remote/BookstoreApi.kt    OkHttp + Bearer token                (37)
│   └── repository/BookRepositoryImpl.kt
├── domain/                              Book, BookRepository, GetBooksUseCase
└── presentation/
    ├── di/AppModule.kt                  @Singleton AuthManager, TokenStore, BookstoreApi
    ├── viewmodel/AppViewModel.kt        UiState 3 trạng thái                 (107)
    └── ui/MainActivity.kt               ComponentActivity + Compose          (184)
```

So sánh trực tiếp với `bookstore-android-biometric`:

| | `bookstore-android` (A) | `bookstore-android-biometric` (B) |
|---|---|---|
| Activity base class | `ComponentActivity` | `FragmentActivity` (bắt buộc bởi `androidx.biometric`) |
| Dependency sinh trắc học | **không có** | `androidx.biometric:1.2.0-alpha05` |
| `minSdk` | 26 | 28 (Keystore user-auth-bound key) |
| Lớp lưu trữ | `TokenStore` (30 dòng) | `BiometricVault` (130 dòng) |
| Quyền Manifest | `INTERNET` | `INTERNET` + `USE_BIOMETRIC` |
| `ConnectionBuilder` tuỳ biến | không cần | cần (cleartext) |
| Tổng LOC | ~600 | ~950 |

Chênh lệch này chính là "chi phí kỹ thuật" của hướng B — và ngược lại là bằng chứng cho thấy hướng A đẩy được toàn bộ độ phức tạp về phía IdP.

### Cấu hình build — `app/build.gradle.kts`

```kotlin
defaultConfig {
    applicationId = "com.snp.bookstore"
    minSdk = 26
    targetSdk = 35
    manifestPlaceholders["appAuthRedirectScheme"] = "com.snp.bookstore"

    // KEYCLOAK_BASE_URL bắt buộc là HTTPS thật (domain ngrok) — Chrome trên Android
    // KHÔNG coi 10.0.2.2/IP LAN là secure context nên WebAuthn/passkey sẽ bị chặn.
    buildConfigField("String", "KEYCLOAK_BASE_URL",
        "\"${project.findProperty("kcBaseUrl") ?: "https://<ngrok>.ngrok-free.dev"}\"")
    buildConfigField("String", "KEYCLOAK_REALM",     "\"test\"")
    buildConfigField("String", "KEYCLOAK_CLIENT_ID", "\"passwordless-demo\"")

    // BOOKSTORE_API_BASE_URL không làm WebAuthn nên không cần HTTPS.
    buildConfigField("String", "BOOKSTORE_API_BASE_URL",
        "\"${project.findProperty("apiBaseUrl") ?: "http://192.168.x.x:3043"}\"")
}
```

Hai endpoint có yêu cầu bảo mật **bất đối xứng**, và đây là chi tiết dễ nhầm nhất khi dựng môi trường:

| Endpoint | Scheme | Vì sao |
|---|---|---|
| `KEYCLOAK_BASE_URL` | **HTTPS bắt buộc** | Chrome chỉ cho gọi `navigator.credentials.*` trong secure context |
| `BOOKSTORE_API_BASE_URL` | HTTP là đủ | Chỉ là REST + Bearer, không đụng WebAuthn |

Dùng `project.findProperty()` để override qua `-PkcBaseUrl=...` khi build, không phải sửa file mỗi lần đổi domain ngrok.

Dependency:

| Thư viện | Version | Dùng để |
|---|---|---|
| `net.openid:appauth` | 0.11.1 | Authorization Code + PKCE, token endpoint |
| `androidx.browser:browser` | 1.8.0 | Custom Tabs — **bắt buộc để WebAuthn hoạt động** |
| `com.google.dagger:hilt-android` | 2.51.1 | DI |
| `com.squareup.okhttp3:okhttp` | 4.12.0 | Gọi `bookstore-api-mobile` |
| `kotlinx-serialization-json` | 1.7.3 | Parse response |

Danh sách này **không có** thư viện WebAuthn/FIDO/Credential Manager nào — đó là điều đáng chú ý nhất.

### `AndroidManifest.xml`

```xml
<uses-permission android:name="android.permission.INTERNET" />

<application android:usesCleartextTraffic="true" ...>
    <activity android:name=".presentation.ui.MainActivity"
              android:exported="true"
              android:launchMode="singleTask">   <!-- tránh tạo instance mới khi deep link về -->
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent-filter>
    </activity>

    <!-- AppAuth bắt redirect từ Custom Tab -->
    <activity android:name="net.openid.appauth.RedirectUriReceiverActivity"
              android:exported="true">
        <intent-filter>
            <action android:name="android.intent.action.VIEW" />
            <category android:name="android.intent.category.DEFAULT" />
            <category android:name="android.intent.category.BROWSABLE" />
            <data android:scheme="com.snp.bookstore" android:path="/oauth2redirect" />
        </intent-filter>
    </activity>
</application>
```

Chỉ một quyền `INTERNET` — không cần `USE_BIOMETRIC`, vì việc quét vân tay do Chrome + hệ thống thực hiện, không phải app. `usesCleartextTraffic` ở đây chỉ phục vụ gọi `bookstore-api-mobile` qua HTTP LAN.

---

## 5.4 Luồng đăng ký passkey (lần đầu)

```
User bấm "Đăng nhập bằng Passkey"
   │
   ├─ AppViewModel.onLoginStarted()  → UiState.LoggingIn
   ├─ AuthManager.buildLoginIntent() → Custom Tab mở /auth?client_id=passwordless-demo
   │                                    &code_challenge=...&code_challenge_method=S256
   ▼
Keycloak chạy flow "Browser Passwordless"
   ├─ auth-cookie: chưa có session → fail, sang ALTERNATIVE kế tiếp
   ├─ webauthn-authenticator-passwordless: user chưa có passkey → fail
   └─ auth-username-password-form: user nhập passkey-demo / 123456   ← lần duy nhất
   │
   ▼
Required action "webauthn-register-passwordless" kích hoạt
   ├─ navigator.credentials.create({ authenticatorSelection: {
   │       authenticatorAttachment: "platform",
   │       residentKey: "required",
   │       userVerification: "required" } })
   ├─ Android Credential Manager → quét vân tay → sinh cặp khoá ES256
   └─ POST public key + credentialId về Keycloak, lưu vào credential store
   │
   ▼
302 → com.snp.bookstore:/oauth2redirect?code=...
   ├─ RedirectUriReceiverActivity → loginLauncher
   ├─ handleAuthorizationResponse() → performTokenRequest(code + PKCE verifier)
   └─ AppViewModel.onLoginResult(AuthState) → TokenStore.save() → loadBooks()
```

## 5.5 Luồng đăng nhập lại bằng passkey

```
User bấm "Đăng nhập bằng Passkey"
   │
   ▼
Keycloak chạy flow "Browser Passwordless"
   ├─ auth-cookie: hết session → sang bước sau
   └─ webauthn-authenticator-passwordless:
        ├─ Keycloak gửi challenge + allowCredentials: []   ← rỗng vì residentKey
        ├─ navigator.credentials.get() → Credential Manager liệt kê passkey cho RP
        ├─ User chọn tài khoản + quét vân tay              ← KHÔNG nhập username
        ├─ Private key ký challenge (không rời thiết bị)
        └─ Keycloak verify chữ ký bằng public key đã lưu → xác định user từ credentialId
   │
   ▼
302 → deep link + code → token → UiState.LoggedIn
```

Toàn bộ tương tác của người dùng: **một lần chạm vân tay**. Không gõ ký tự nào.

---

## 5.6 Ràng buộc HTTPS (secure context) — rào cản lớn nhất

Trình duyệt chỉ cho gọi `navigator.credentials.*` trong **secure context**:

- `https://` bất kỳ domain nào, hoặc
- literal `localhost` / `127.0.0.1`.

Trên emulator Android, `10.0.2.2` (alias trỏ về máy host) **không** được Chrome coi là localhost → WebAuthn bị chặn thẳng, và thông báo lỗi của trình duyệt rất mơ hồ. Tương tự với IP LAN `192.168.x.x` trên thiết bị thật.

Cách xử lý trong PoC là dựng ngrok làm HTTPS termination trước Keycloak:

```bash
ngrok http 8080
# → https://natant-kinesically-easter.ngrok-free.dev
KC_HOSTNAME=natant-kinesically-easter.ngrok-free.dev docker compose up -d keycloak
./gradlew installDebug -PkcBaseUrl=https://natant-kinesically-easter.ngrok-free.dev
```

Docker Compose phải khai báo tương ứng:

```yaml
keycloak:
  image: quay.io/keycloak/keycloak:26.1.4
  command: start-dev --import-realm
  environment:
    KC_HOSTNAME_STRICT: "false"
    KC_HOSTNAME_STRICT_HTTPS: "false"
    KC_HTTP_ENABLED: "true"
    KC_HOSTNAME: ${KC_HOSTNAME:-}     # domain ngrok khi test thiết bị thật
    KC_PROXY_HEADERS: xforwarded      # ngrok gửi X-Forwarded-*, KHÔNG phải header RFC 7239
```

Ba chi tiết dễ sai:

- **`KC_PROXY_HEADERS: xforwarded`** — thiếu dòng này, Keycloak tưởng request đến qua `http` (vì ngrok terminate TLS rồi mới forward HTTP vào container), sinh ra redirect URI sai scheme và origin không khớp với origin trình duyệt gửi lên trong WebAuthn assertion.
- **`KC_HOSTNAME` phải khớp domain ngrok** — vì `webAuthnPolicyPasswordlessRpId` để rỗng, Keycloak suy rpId từ hostname. Nếu hostname sai, passkey đã đăng ký sẽ không dùng được.
- **Đổi domain ngrok = passkey cũ chết** — rpId thay đổi, credential cũ không còn khớp RP. Free tier ngrok cấp domain mới mỗi lần restart, nên phải dùng domain cố định (`--domain=...`) hoặc đăng ký lại passkey.

Ngoài ra, volume `keycloak-db` dùng import strategy `IGNORE_EXISTING`: một khi volume đã có dữ liệu, sửa `test-realm.json` sẽ **không** tự áp dụng lại. Muốn ép re-import phải `docker volume rm keycloak_keycloak-db`.

---

## 5.7 Hiện thực phía app

### `AuthManager.kt` — bọc AppAuth

```kotlin
class AuthManager(context: Context) {
    private val redirectUri = "com.snp.bookstore:/oauth2redirect".toUri()
    private val service = AuthorizationService(context)   // DefaultConnectionBuilder, không override

    private val serviceConfig = AuthorizationServiceConfiguration(
        ".../protocol/openid-connect/auth".toUri(),
        ".../protocol/openid-connect/token".toUri(),
        null,
        ".../protocol/openid-connect/logout".toUri(),
    )

    fun buildLoginIntent(): Intent {
        val request = AuthorizationRequest.Builder(
            serviceConfig, BuildConfig.KEYCLOAK_CLIENT_ID, ResponseTypeValues.CODE, redirectUri,
        )
            .setScope("openid profile email")   // không có offline_access — khác hướng B
            .build()
        return service.getAuthorizationRequestIntent(request)
    }
}
```

Khác biệt so với hướng B đáng ghi nhận: `AuthorizationService(context)` dùng **`DefaultConnectionBuilder` mặc định**, không cần `CleartextConnectionBuilder`. Vì Keycloak đã ở sau HTTPS ngrok (điều kiện bắt buộc của WebAuthn), rào cản HTTPS của AppAuth tự nhiên được thoả mãn — một ràng buộc lại vô tình giải quyết một ràng buộc khác.

Đổi code lấy token:

```kotlin
fun handleAuthorizationResponse(intent: Intent, onResult: (AuthState?, AuthorizationException?) -> Unit) {
    val response = AuthorizationResponse.fromIntent(intent)
    val error = AuthorizationException.fromIntent(intent)
    if (response == null) { onResult(null, error); return }

    // PKCE code_verifier đã được AppAuth tự lưu kèm response, không cần tự quản lý.
    service.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, exception ->
        if (tokenResponse != null) {
            val state = AuthState(response, exception).apply { update(tokenResponse, exception) }
            onResult(state, null)
        } else onResult(null, exception)
    }
}
```

Đăng xuất mở browser để xoá SSO cookie phía Keycloak — nếu chỉ xoá token cục bộ, nhánh `auth-cookie` trong flow sẽ cho vào thẳng ở lần đăng nhập kế tiếp mà không hỏi passkey:

```kotlin
fun buildLogoutIntent(idToken: String?): Intent {
    val params = mutableMapOf("post_logout_redirect_uri" to redirectUri.toString())
    idToken?.let { params["id_token_hint"] = it }
    ...
    return Intent(Intent.ACTION_VIEW, uri)
}
```

### `TokenStore.kt` — lưu phiên

```kotlin
class TokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("auth_state", Context.MODE_PRIVATE)

    fun save(state: AuthState) = prefs.edit { putString(KEY, state.jsonSerializeString()) }
    fun read(): AuthState? = prefs.getString(KEY, null)?.let { AuthState.jsonDeserialize(it) }
    fun clear() = prefs.edit { remove(KEY) }
}
```

Toàn bộ 30 dòng. `AuthState.jsonSerializeString()` của AppAuth đã gói sẵn access/refresh/id token cùng metadata hết hạn.

**Đây là điểm yếu bảo mật có chủ đích của PoC**: token nằm plaintext trong SharedPreferences, chỉ được bảo vệ bởi app sandbox. Máy đã root là đọc được. Production phải dùng `EncryptedSharedPreferences` — hoặc chính xác hơn, đây là chỗ mà hướng B (`BiometricVault`) đã giải quyết triệt để. Hai hướng bổ sung cho nhau ở đúng điểm này.

### `MainActivity.kt` — chỉ 2 launcher

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {          // ComponentActivity là đủ, không cần FragmentActivity

    private val loginLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        val data = result.data ?: run { viewModel.onLoginResult(null); return@registerForActivityResult }
        viewModel.authManager.handleAuthorizationResponse(data) { state, _ ->
            runOnUiThread { viewModel.onLoginResult(state) }
        }
    }

    // Đăng xuất SSO ở Keycloak (mở browser xoá cookie), không chờ kết quả trả về —
    // token cục bộ đã được xoá ngay ở onLogoutClick.
    private val logoutBrowserLauncher = registerForActivityResult(StartActivityForResult()) {}
}
```

`runOnUiThread` là cần thiết vì callback của AppAuth chạy trên background thread, còn `MutableStateFlow` của ViewModel đang được Compose observe trên main thread.

So sánh trực quan: `MainActivity` của hướng B dài 343 dòng với 2 hàm `BiometricPrompt`, cờ `awaitingBiometricEnrollment`, override `onResume()`, và hàm `startBiometricEnrollmentFlow()` xử lý 4 nhánh lỗi. Ở đây là 184 dòng, phần lớn là Compose UI.

### `AppViewModel.kt` — state machine

```kotlin
sealed interface UiState {
    data object LoggedOut : UiState                  // không có savedUsername như hướng B
    data object LoggingIn : UiState
    data class LoggedIn(
        val username: String,
        val books: List<Book> = emptyList(),
        val loadingBooks: Boolean = false,
        val error: String? = null,
    ) : UiState
}
```

Khôi phục phiên khi mở lại app:

```kotlin
init {
    tokenStore.read()?.let { restored ->
        if (restored.isAuthorized) {
            authState = restored
            _uiState.value = UiState.LoggedIn(username = "…")
            loadBooks()
        }
    }
}
```

Ở đây có một hạn chế nhỏ đã biết: username hiển thị tạm là `"…"` vì code không decode lại `id_token` từ state khôi phục, chỉ decode ở `onLoginResult`. Sửa được bằng cách gọi `decodePreferredUsername(restored.idToken)` — chưa xử lý vì không ảnh hưởng luồng xác thực.

Username lấy từ claim `preferred_username`, decode Base64 URL-safe thủ công, **không verify chữ ký** — việc verify là của backend:

```kotlin
private fun decodePreferredUsername(idToken: String): String? = runCatching {
    var payload = idToken.split(".")[1]
    payload += "=".repeat((4 - (payload.length % 4)) % 4)
    val decoded = String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP))
    Regex("\"preferred_username\"\\s*:\\s*\"([^\"]+)\"").find(decoded)?.groupValues?.get(1)
}.getOrNull()
```

### Gọi API nghiệp vụ

```kotlin
val request = Request.Builder()
    .url("${BuildConfig.BOOKSTORE_API_BASE_URL}/api/books")
    .header("Authorization", "Bearer $accessToken")
    .build()
```

`bookstore-api-mobile` verify JWT bằng JWKS của Keycloak, **không cần `client_secret`** vì `passwordless-demo` là public client. Backend hoàn toàn không biết token đến từ passkey hay từ mật khẩu — đúng như hướng B.

---

## 5.8 Những rào cản đã gặp và cách xử lý

| Vấn đề | Triệu chứng | Cách xử lý |
|---|---|---|
| WebAuthn cần secure context | Nút passkey không hiện / `navigator.credentials` undefined trên `10.0.2.2` | ngrok HTTPS trước Keycloak, `KC_HOSTNAME` = domain ngrok |
| ngrok terminate TLS | Redirect URI sai scheme, origin mismatch khi verify assertion | `KC_PROXY_HEADERS: xforwarded` |
| rpId suy từ hostname | Đổi domain ngrok → passkey cũ vô hiệu | Dùng ngrok domain cố định, hoặc đăng ký lại passkey |
| Import realm bị bỏ qua | Sửa `test-realm.json` không có tác dụng | `IGNORE_EXISTING` — phải `docker volume rm keycloak_keycloak-db` |
| Không dùng WebView được | WebView không hỗ trợ WebAuthn API | Bắt buộc Custom Tabs (`androidx.browser`) — cũng đúng chuẩn RFC 8252 |
| `RequireResidentKey` chưa bật | Vẫn phải nhập username trước khi quét vân tay | Đặt `"Yes"` để credential là discoverable |
| Đăng xuất không triệt để | Lần sau vào thẳng, không hỏi passkey | Mở browser gọi endpoint logout với `id_token_hint`, không chỉ xoá token cục bộ |
| Thay đổi flow ảnh hưởng client khác | Rủi ro downtime cho app đang chạy | `authenticationFlowBindingOverrides` ở mức client thay vì đổi browser flow của realm |
| Callback AppAuth ở background thread | Compose không cập nhật UI | `runOnUiThread` trước khi gọi ViewModel |

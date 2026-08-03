# Bookstore Android — Passwordless WebAuthn Demo

App Android native (Kotlin + Jetpack Compose) đăng nhập Keycloak bằng
**passkey (WebAuthn passwordless)** qua Chrome Custom Tabs + AppAuth
(Authorization Code + PKCE), sau đó gọi `bookstore-api-mobile` để hiển thị
danh sách sách.

## Vì sao không dùng WebView?

WebAuthn (Face ID/vân tay/PIN) cần tích hợp Android Credential Manager mà
WebView không cung cấp đáng tin cậy, và Google đã cấm OAuth login trong
WebView từ 2016. App này mở **Chrome Custom Tab** — browser thật — nên
passkey hoạt động y hệt khi bạn mở Keycloak trên Chrome bình thường.

## Luồng đăng nhập

```
App bấm "Đăng nhập bằng Passkey"
  → mở Custom Tab tới Keycloak /auth (client "passwordless-demo", PKCE)
  → user xác nhận Face ID / vân tay / PIN (WebAuthn)
  → Keycloak redirect: com.snp.bookstore:/oauth2redirect?code=...
  → Android bắt deep link (RedirectUriReceiverActivity của AppAuth)
  → app đổi code lấy token (PKCE code_verifier, không cần client_secret)
  → gọi bookstore-api-mobile với Authorization: Bearer <access_token>
```

## Cấu trúc

```
app/src/main/java/com/snp/bookstore/
├── MainActivity.kt        # Compose UI: Login / Loading / Books screen
├── AppViewModel.kt        # State machine login/logout/load books
├── auth/AuthManager.kt    # AppAuth: build authorization/logout intent, đổi code lấy token
├── auth/TokenStore.kt     # Lưu AuthState (SharedPreferences, demo only)
├── network/BookstoreApi.kt# Gọi GET /api/books với Bearer token
└── model/Book.kt          # DTO
```

## Yêu cầu trước khi chạy

1. **Realm `test` đã import** (`keycloak-config/test-realm.json`) với:
   - Client `passwordless-demo` (public, PKCE, flow "Browser Passwordless")
   - User `passkey-demo` / mật khẩu `123456` (bị bắt đăng ký passkey lần đầu)
   - Redirect URI đã có sẵn: `com.snp.bookstore:/oauth2redirect`
2. **`bookstore-api-mobile` đang chạy** (port 3043) — verify JWT qua JWKS,
   không cần client_secret.
3. **WebAuthn cần secure context** — xem phần Emulator vs Thiết bị thật bên dưới.

## Mở project

Mở thư mục `bookstore-android/` bằng **Android Studio** (Koala trở lên) —
Android Studio sẽ tự tạo Gradle Wrapper khi sync lần đầu nếu chưa có.
Yêu cầu JDK 17 (Android Studio thường đã kèm sẵn).

## Chạy trên Android Emulator (nhanh nhất, không cần ngrok)

Emulator coi `10.0.2.2` là secure context tương đương localhost của máy host,
nên WebAuthn hoạt động được **mà không cần HTTPS/ngrok** — miễn Keycloak
chạy ở `localhost:8080` trên máy host.

1. `docker compose up -d keycloak bookstore-api-mobile` (từ thư mục gốc repo).
2. Build & Run app trên Android Emulator (không cần thay đổi gì — mặc định
   `KEYCLOAK_BASE_URL=http://10.0.2.2:8080`, `BOOKSTORE_API_BASE_URL=http://10.0.2.2:3043`).
3. Bấm "Đăng nhập bằng Passkey" → Custom Tab mở → đăng nhập
   `passkey-demo` / `123456` → được yêu cầu đăng ký passkey → xác nhận
   bằng màn hình khóa emulator (vân tay ảo/PIN) → quay lại app thấy
   danh sách sách.
4. Đăng xuất, đăng nhập lại — lần này không cần password, chỉ passkey.

> Lưu ý: passkey đăng ký trên emulator gắn với thiết bị ảo đó (Android
> Keystore), không đồng bộ sang Google Password Manager trừ khi emulator
> có đăng nhập tài khoản Google và bật đồng bộ passkey.

## Chạy trên điện thoại thật (cần HTTPS qua ngrok)

Điện thoại thật không coi `10.0.2.2`/`localhost` của máy tính là secure
context, nên **bắt buộc HTTPS**:

1. `ngrok http 8080` → lấy domain `https://xxxx.ngrok-free.app`.
2. Restart Keycloak với hostname đó:
   ```bash
   KC_HOSTNAME=xxxx.ngrok-free.app docker compose up -d keycloak
   ```
3. Cũng cần expose `bookstore-api-mobile` ra HTTPS nếu muốn gọi từ điện
   thoại thật (ví dụ `ngrok http 3043` ở tab thứ hai) — hoặc dùng chung
   máy tính + điện thoại trong cùng mạng LAN và trỏ `apiBaseUrl` sang
   `http://<IP-LAN-máy-tính>:3043` (API không cần WebAuthn nên không bắt
   buộc HTTPS, chỉ Keycloak login mới cần).
4. Build APK với 2 giá trị override:
   ```bash
   ./gradlew assembleDebug \
     -PkcBaseUrl=https://xxxx.ngrok-free.app \
     -PapiBaseUrl=http://<IP-LAN-máy-tính>:3043
   ```
5. Cài APK lên điện thoại (`adb install app/build/outputs/apk/debug/app-debug.apk`),
   mở app, đăng nhập bằng Face ID/vân tay của chính điện thoại đó.

## Đổi user/client

Muốn test với user khác hoặc client khác, sửa
`buildConfigField` trong [app/build.gradle.kts](app/build.gradle.kts)
(`KEYCLOAK_CLIENT_ID`) — client đó phải có redirect URI
`com.snp.bookstore:/oauth2redirect` khai báo trong Keycloak Admin Console
(Clients → Settings → Valid redirect URIs).

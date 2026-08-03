# Bookstore Android — Biometric App-Lock Demo

App Android native (Kotlin + Jetpack Compose) minh hoạ mô hình
**"đăng nhập Keycloak một lần, các lần sau chỉ cần vân tay"** — khác hẳn
[bookstore-android/](../bookstore-android/) (WebAuthn passkey qua Keycloak).
Ở đây, sinh trắc học hoàn toàn cục bộ trên thiết bị, Keycloak không biết gì
về vân tay/Face ID — nó chỉ thấy access/refresh token bình thường.

## Cơ chế

1. **Lần đăng nhập đầu tiên**: app mở Chrome Custom Tab, user nhập
   username/password qua Keycloak (client `biometric-demo`, Authorization
   Code + PKCE, xin thêm scope `offline_access`). Đổi code lấy
   `access_token` + `refresh_token`.
2. Ngay sau đó, app xin vân tay **một lần** để mã hoá `refresh_token` bằng
   khoá AES nằm trong **Android Keystore** (`setUserAuthenticationRequired`
   — khoá này chỉ dùng được sau khi xác thực sinh trắc học thành công) rồi
   lưu bản mã hoá vào SharedPreferences.
3. **Các lần mở app sau**: thấy có refresh_token đã lưu → tự động bật
   `BiometricPrompt` → quét vân tay → giải mã refresh_token → gọi thẳng
   Keycloak token endpoint với `grant_type=refresh_token` để lấy
   `access_token` mới, **không mở lại Custom Tab, không cần nhập lại mật khẩu**.
4. Access token mới dùng gọi `bookstore-api-mobile` như bình thường.

```
Lần đầu:
  App --Custom Tab--> Keycloak (username/password) --code--> App
  App --refresh_token--> [vân tay ENCRYPT] --> Android Keystore + SharedPrefs

Các lần sau:
  App --có sẵn ciphertext--> [vân tay DECRYPT] --> refresh_token
  App --refresh_token--> Keycloak /token (grant_type=refresh_token) --> access_token mới
  App --Bearer access_token--> bookstore-api-mobile
```

**Điểm mấu chốt bảo mật**: private/secret key mã hoá không bao giờ rời khỏi
Android Keystore và không thể dùng được nếu thiếu xác thực sinh trắc học
hợp lệ trong phiên đó — kể cả nếu ai đó trích xuất được file
SharedPreferences (ciphertext), họ vẫn không giải mã được nếu không phải
chính thiết bị + vân tay đã đăng ký.

## Vì sao khác WebAuthn passkey

| | WebAuthn passkey (`bookstore-android/`) | Biometric app-lock (app này) |
|---|---|---|
| Ai biết về vân tay? | Keycloak (WebAuthn credential lưu ở server) | Chỉ app, cục bộ trên thiết bị |
| Cần Custom Tab mỗi lần đăng nhập? | Có — mỗi lần "Sign in with passkey" đều qua Keycloak | Không — chỉ lần đầu tiên |
| Cần HTTPS (ngrok) cho vân tay? | Có — WebAuthn API cần secure context | Không — BiometricPrompt là API Android thuần, không phải web |
| Đổi thiết bị | Phải đăng ký passkey mới trên thiết bị mới | Phải đăng nhập lại bằng password trên thiết bị mới (vault không đồng bộ) |
| Mất thiết bị | Passkey cũ vẫn ở server, revoke được qua Admin Console | Refresh token vẫn hợp lệ tới khi hết hạn (30 ngày) hoặc bị revoke thủ công |

Vì `BiometricPrompt` không liên quan WebAuthn/web, **không cần ngrok chỉ để
xác thực vân tay** — bạn vẫn cần Keycloak khả dụng qua mạng (HTTP thường
cũng được) vì app phải gọi được token endpoint.

## Cấu trúc

```
app/src/main/java/com/snp/bookstorebio/
├── MainActivity.kt          # Compose UI + nơi thực thi BiometricPrompt thật
├── AppViewModel.kt          # State machine: LoggedOut / LoggingIn / LockedBiometric / LoggedIn
├── auth/AuthManager.kt      # AppAuth: authorization code (lần đầu) + refresh_token grant (các lần sau)
├── auth/BiometricVault.kt   # AES key trong Android Keystore, mã hoá/giải mã refresh_token
├── network/BookstoreApi.kt  # Gọi GET /api/books với Bearer token
└── model/Book.kt            # DTO
```

## Cấu hình Keycloak liên quan

Client `biometric-demo` trong `keycloak-config/test-realm.json`:
- `publicClient: true`, PKCE bắt buộc (không cần `client_secret`).
- `defaultClientScopes` có `offline_access` — bắt buộc để nhận
  `refresh_token` sống lâu, không phụ thuộc SSO session ngắn.
- `client.offline.session.max.lifespan` / `idle.timeout` = 2592000s (30
  ngày) — refresh_token dùng được tối đa 30 ngày trước khi phải đăng nhập
  lại bằng password.
- Flow đăng nhập dùng `browser` mặc định (username/password bình thường)
  — **không** gán flow WebAuthn như `passwordless-demo`.

## Yêu cầu trước khi chạy

1. Realm `test` đã import với client `biometric-demo` và user `passkey-demo`
   (hoặc bất kỳ user nào có password) — Admin Console → Clients →
   `biometric-demo` để xác nhận.
2. `bookstore-api-mobile` đang chạy (port 3043).
3. Thiết bị/emulator đã đăng ký ít nhất 1 phương thức sinh trắc học
   (vân tay/Face) hoặc khoá màn hình mạnh (PIN/pattern) — `BiometricPrompt`
   yêu cầu điều này để hoạt động.

## Chạy trên Android Emulator

```bash
docker compose up -d keycloak bookstore-api-mobile
```

Build & Run app — mặc định trỏ `KEYCLOAK_BASE_URL=https://<domain-ngrok>`
(đổi qua `-PkcBaseUrl=...` nếu Keycloak chạy chỗ khác) và
`BOOKSTORE_API_BASE_URL=http://192.168.x.x:3043` (đổi qua `-PapiBaseUrl=...`).
Vì app này không cần WebAuthn, bạn cũng có thể trỏ thẳng
`http://10.0.2.2:8080` cho `KEYCLOAK_BASE_URL` khi test trên emulator —
không bắt buộc HTTPS.

1. Bấm "Đăng nhập" → Custom Tab mở → nhập `passkey-demo` / `123456`.
2. Ngay sau đăng nhập, app xin vân tay để lưu — xác nhận trên emulator
   (vân tay ảo qua `adb -e emu finger touch 1` hoặc PIN đã đặt).
3. Thấy danh sách sách. Đóng hẳn app (kill process), mở lại.
4. Lần này vào thẳng màn "Bookstore đã khoá", tự động bật vân tay — xác
   nhận lại → vào thẳng danh sách sách, **không cần nhập password**.

## Xoá vault / đăng xuất

Bấm "Đăng xuất" trong app: xoá khoá Keystore + ciphertext đã lưu, kết thúc
SSO session Keycloak. Lần sau mở app phải đăng nhập lại bằng password.

## Trường hợp đặc biệt: đổi vân tay trên thiết bị

Nếu người dùng thêm/xoá vân tay đã đăng ký trên chính thiết bị (Settings →
Security), Android **tự động huỷ** mọi khoá Keystore có
`setUserAuthenticationRequired(true)` — đây là hành vi bảo mật mặc định của
hệ điều hành, không phải lỗi. App bắt lỗi này qua
`KeyPermanentlyInvalidatedException` (xem `isKeyInvalidated()` trong
`BiometricVault.kt`) và tự động xoá vault, đưa user về màn đăng nhập lại
bằng password.

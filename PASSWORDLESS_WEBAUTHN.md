# Passwordless WebAuthn — Keycloak + Android

Tài liệu này giải thích cơ chế đăng nhập không mật khẩu (passkey/WebAuthn)
đã triển khai cho client `passwordless-demo`, dùng bởi app
[bookstore-android/](bookstore-android/) và backend
[bookstore-api-mobile/](bookstore-api-mobile/). Khác với các demo OAuth khác
trong repo này (username/password qua `test-client`, `test-client-2`...),
đây là luồng xác thực bằng sinh trắc học (Face ID / vân tay / PIN) thay cho
mật khẩu.

## WebAuthn là gì, tại sao cần

WebAuthn là chuẩn W3C cho phép trình duyệt/thiết bị tạo ra một **cặp khóa
public/private** gắn với một dịch vụ (Relying Party — ở đây là Keycloak) và
một thiết bị cụ thể:

- **Private key** không bao giờ rời khỏi thiết bị — được giữ trong secure
  storage (Android Keystore, iOS Secure Enclave...), khoá bằng sinh trắc học
  của chính thiết bị đó.
- **Public key** được gửi lên server (Keycloak) lúc đăng ký, dùng để xác minh
  chữ ký ở các lần đăng nhập sau.

Vì private key không rời thiết bị, không có mật khẩu nào để lộ, bị đánh cắp
qua phishing, hay bị dò brute-force ở phía server.

## Vì sao không dùng WebView

Việc lấy/tạo WebAuthn credential đòi hỏi tích hợp **Android Credential
Manager** (hoặc iOS tương đương) — chỉ trình duyệt thật (Chrome, Safari...)
mới có tích hợp này. WebView nhúng trong app không hỗ trợ đáng tin cậy, và
Google đã cấm luồng OAuth login qua WebView từ 2016 (rủi ro phishing vì app
có thể đọc trộm form đăng nhập).

→ Giải pháp: app Android mở **Chrome Custom Tab** — một cửa sổ trình duyệt
thật lồng trong app, dùng chung cookie/session với Chrome, nhưng vẫn tách
biệt code khỏi app để không đọc trộm được nội dung trang.

## Kiến trúc tổng thể

```
Android App ──mở──> Chrome Custom Tab ──HTTPS──> Keycloak (qua ngrok)
     ↑                                                   │
     │                                          navigator.credentials.*
     │                                                   ↓
     │                                     Android Credential Manager
     │                                     (Face ID / vân tay / PIN)
     │
     └──deep link──  com.snp.bookstore:/oauth2redirect?code=...
                              │
                              ↓
                    đổi code lấy token (PKCE)
                              │
                              ↓
              Bearer token → bookstore-api-mobile (verify qua JWKS)
```

**Các thành phần:**

| Thành phần | Vai trò |
|---|---|
| `passwordless-demo` (Keycloak client) | Public client (không có `client_secret`), PKCE bắt buộc, dùng riêng cho luồng passwordless |
| `Browser Passwordless` (Authentication Flow) | Flow tùy chỉnh: cookie SSO → nút "Sign in with passkey" → fallback username/password |
| `webauthn-register-passwordless` (Required Action) | Bắt buộc user đăng ký passkey lần đầu (gán cho user demo `passkey-demo`) |
| ngrok tunnel | Expose Keycloak (`:8080`) ra HTTPS công khai — bắt buộc vì WebAuthn chỉ chạy trên "secure context" |
| `bookstore-android` | App Kotlin + Compose, dùng AppAuth để làm Authorization Code + PKCE qua Custom Tab |
| `bookstore-api-mobile` | API sách, verify access token bằng chữ ký JWT (JWKS), không cần `client_secret` |

## Tại sao bắt buộc HTTPS (ngrok)

Trình duyệt chỉ cho phép gọi WebAuthn API (`navigator.credentials.create/get`)
trong "secure context":

- `https://` bất kỳ domain nào, **hoặc**
- domain đúng literal `localhost` / `127.0.0.1` (ngoại lệ đặc biệt cho dev).

`10.0.2.2` (địa chỉ máy host nhìn từ Android Emulator) và IP LAN dạng
`192.168.x.x` **không nằm trong ngoại lệ này** — dù bạn đang test cục bộ,
trình duyệt vẫn coi đó là non-secure và chặn hoàn toàn WebAuthn (API trả về
`undefined`, không phải lỗi credential).

→ Giải pháp cho demo: dùng `ngrok http 8080` để có domain HTTPS công khai,
set `KC_HOSTNAME` bằng domain đó, và trỏ app Android gọi thẳng domain này
thay vì `10.0.2.2`.

Lưu ý khi dùng proxy header: ngrok gửi `X-Forwarded-Proto`/`X-Forwarded-Host`
(chuẩn de-facto), **không phải** header `Forwarded` chuẩn RFC 7239 — Keycloak
cần cấu hình đúng `KC_PROXY_HEADERS=xforwarded` (không phải `forwarded`) để
nhận đúng scheme HTTPS, nếu không `issuer` trong token vẫn bị ghi là `http://`
dù request tới từ HTTPS.

## Hai luồng: đăng ký (register) và đăng nhập (sign-in)

### 1. Đăng ký passkey lần đầu

User mới **chưa có credential nào** — bắt buộc phải xác thực bằng cách khác
trước (ở đây là password), Keycloak mới cho phép tạo passkey mới:

1. User mở Custom Tab, thấy 2 lựa chọn: nút "Sign in with passkey" hoặc form
   username/password (do flow có `auth-cookie` + `webauthn-authenticator-passwordless`
   + `auth-username-password-form`, tất cả đều `ALTERNATIVE`).
2. User nhập đúng username/password.
3. User có `requiredActions: ["webauthn-register-passwordless"]` — Keycloak
   redirect sang màn "Set up Passkey" thay vì hoàn tất đăng nhập ngay.
4. Trang gọi `navigator.credentials.create()` — Android Credential Manager
   hiện popup Google Password Manager, yêu cầu Face ID/vân tay/PIN.
5. Thiết bị sinh cặp khóa mới, gửi public key + attestation lên Keycloak.
6. Keycloak lưu credential (loại `webauthn-passwordless`), xoá required
   action, hoàn tất đăng nhập, redirect kèm authorization code.

### 2. Đăng nhập lại bằng passkey

User đã có credential — không cần password nữa:

1. User mở Custom Tab, bấm "Sign in with passkey".
2. Trang gọi `navigator.credentials.get()` **không kèm username** — đây gọi
   là "discoverable credential" / "usernameless" flow: thiết bị phải tự liệt
   kê được passkey nào khớp với `rpId` (domain) đang yêu cầu.
3. Android Credential Manager hiện popup, user xác thực Face ID/vân tay/PIN.
4. Thiết bị ký một challenge bằng private key đã lưu, gửi chữ ký lên Keycloak.
5. Keycloak xác minh chữ ký bằng public key đã lưu → đăng nhập thành công.

## Điểm mấu chốt: Resident Key / Discoverable Credential

Để bước 2 (đăng nhập lại) hoạt động **mà không cần nhập username trước**,
credential tạo ở bước 1 phải là **resident key** (còn gọi là "discoverable
credential") — nghĩa là thiết bị lưu đủ thông tin để tự tìm ra credential
phù hợp với 1 domain, không cần server nói trước "hãy tìm credential ID X".

Nếu policy để mặc định mơ hồ (`not specified`), một số kết hợp
browser/Keycloak có thể tạo ra credential **không phải resident key** — nó
vẫn được lưu ở Keycloak, đăng ký "thành công", nhưng khi sign-in lại theo
kiểu usernameless, thiết bị **không tìm thấy** nó → lỗi
**"No passkeys available"** dù rõ ràng đã đăng ký trước đó.

→ Cấu hình bắt buộc trong `webAuthnPolicyPasswordless*` của realm:

```json
"webAuthnPolicyPasswordlessAuthenticatorAttachment": "platform",
"webAuthnPolicyPasswordlessRequireResidentKey": "Yes",
"webAuthnPolicyPasswordlessUserVerificationRequirement": "required"
```

- `authenticatorAttachment: platform` — chỉ dùng authenticator gắn liền
  thiết bị (Face ID/vân tay/PIN), không phải security key rời (USB/NFC).
- `requireResidentKey: Yes` — ép credential phải discoverable.
- `userVerificationRequirement: required` — bắt buộc xác thực sinh trắc học,
  không chỉ "có mặt" (presence) như một số security key cho phép.

**Hệ quả thực tế**: nếu bạn đổi policy này *sau khi* đã có credential cũ,
credential cũ vẫn giữ thuộc tính lúc tạo — cần xoá và đăng ký lại từ đầu để
áp dụng policy mới.

## Vì sao API backend không dùng token introspection

Các demo OAuth khác trong repo (`bookstore-api-2`...) dùng **token
introspection** (`POST /token/introspect` với `client_secret`) — mỗi request
API phải hỏi lại Keycloak token còn hợp lệ không.

`bookstore-api-mobile` dùng cách khác: **verify chữ ký JWT cục bộ** bằng
public key lấy từ JWKS endpoint (`/protocol/openid-connect/certs`), cache lại
theo `kid`. Lý do:

- Client `passwordless-demo` là **public client**, không có `client_secret`
  để introspect an toàn.
- Verify local nhanh hơn (không cần round-trip mạng mỗi request).
- Đây là pattern chuẩn cho "resource server" nhận Bearer token JWT.

`issuer` cấu hình ở `bookstore-api-mobile/.env` (biến `KEYCLOAK_URL`) phải
**khớp chính xác** domain có trong claim `iss` của token — tức là phải trỏ
domain ngrok, không phải `localhost:8080` nội bộ, vì đó là domain thực sự đã
cấp token cho app.

## Các lỗi thường gặp và nguyên nhân

| Lỗi | Nguyên nhân | Cách xử lý |
|---|---|---|
| `Unexpected error...` khi vào trang login | `providerId` của authenticator trong flow sai (vd `webauthn-passwordless-authenticator` thay vì `webauthn-authenticator-passwordless`) | Sửa đúng providerId, restart Keycloak để re-import realm |
| `navigator.credentials.get()` trả `undefined` | Non-secure context (đang dùng `10.0.2.2`/IP LAN thay vì HTTPS/localhost) | Dùng ngrok cho domain Keycloak |
| `NotAllowedError: timed out or was not allowed` | User chưa có credential nào (đang thử sign-in trước khi đăng ký) | Đăng nhập bằng password trước để trigger required action đăng ký |
| `issuer` trong token là `http://` dù đã qua ngrok HTTPS | `KC_PROXY_HEADERS` sai loại (`forwarded` thay vì `xforwarded`) | Đổi thành `xforwarded` — khớp header ngrok thực sự gửi |
| **"No passkeys available"** dù rõ ràng đã đăng ký | Credential không phải resident key (do policy `not specified`) | Set `requireResidentKey: Yes`, `userVerificationRequirement: required`, `authenticatorAttachment: platform`; xoá credential cũ, đăng ký lại |
| App tự động vào thẳng màn Books, không gọi lại Keycloak | Token cũ còn lưu trong `SharedPreferences` (`TokenStore`) từ lần chạy trước | Clear app data / gỡ cài đặt lại app trước khi test lại từ đầu |

## Giới hạn cần biết khi demo

- **ngrok free tier**: domain đổi mỗi lần restart tunnel, chỉ cho 1 tunnel
  đồng thời trên 1 tài khoản, và hiện interstitial cảnh báo lần đầu mỗi
  phiên browser (bấm "Visit Site" để qua).
- **Android Emulator**: bắt buộc dùng AVD loại **"Google Play"** (không phải
  "Google APIs"), có đăng nhập Google account và đặt khoá màn hình (PIN) —
  thiếu 1 trong 3 điều kiện này, Credential Manager không hoạt động đầy đủ.
- **Passkey gắn với thiết bị + rpId**: đăng ký trên emulator A không dùng
  được khi chuyển sang emulator B (trừ khi cùng Google account và đã đồng
  bộ), và đổi domain ngrok giữa 2 lần cũng làm credential cũ mất tác dụng vì
  `rpId` không khớp.

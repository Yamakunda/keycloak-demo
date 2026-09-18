# qr-login-spi

Custom Keycloak SPI cho luồng "Đăng nhập bằng QR" cross-device: người dùng đang mở trang login Keycloak trên máy tính, chọn "Try another way" → "Đăng nhập bằng QR", quét mã bằng app
di động đã đăng nhập sẵn, xác nhận trên điện thoại (biometric/mật khẩu) rồi trang trên máy tính tự đăng nhập — không cần gõ username/password trên máy tính.

## Kiến trúc

Gồm hai phần đăng ký qua `META-INF/services`:

- **`QrLoginAuthenticatorFactory`** (id `qr-login-authenticator`) — một bước ALTERNATIVE
  trong Browser Flow, song song với Username Password Form. Khi được chọn, render theme
  `qr-login.ftl` hiển thị mã QR và tự poll trạng thái.
- **`QrLoginResourceProviderFactory`** (id `qr-login`) — REST endpoint tùy biến tại
  `/realms/{realm}/qr-login/*`, dùng để app di động và trang login trao đổi trạng thái phiên.

Trạng thái phiên QR lưu tạm trong bộ nhớ (`QrLoginSessionStore`, `ConcurrentHashMap`), đủ cho
demo 1 node Keycloak. Chạy nhiều node (cluster) cần chuyển sang Infinispan cache.

### Luồng hoạt động

```
1. Trình duyệt vào Browser Flow, chọn "Đăng nhập bằng QR"
   → QrLoginAuthenticator.authenticate() tạo session (status=pending), render qr-login.ftl

2. Trang login hiện mã QR chứa {apiUrl, sessionId}, JS bắt đầu poll POST /qr-login/check
   mỗi 2s, đồng thời hiện đồng hồ đếm ngược thời gian còn lại

3. App di động quét QR, gọi POST /qr-login/scan kèm Bearer access_token của người dùng đã đăng nhập trên điện thoại → session chuyển sang status=scanned (đã biết là ai,
   NHƯNG CHƯA cho phép trình duyệt đăng nhập)

4. App tự hiển thị màn hình xác nhận (Face ID / vân tay / nhập lại mật khẩu) trên chính  điện thoại đó

5a. Xác nhận thành công → app gọi POST /qr-login/approve → session chuyển sang
    status=approved
5b. Người dùng từ chối / xác thực thất bại → app gọi POST /qr-login/cancel → session
    quay về status=pending để trình duyệt vẫn chờ quét lại

6. Trang login (đang poll /qr-login/check) thấy status=approved → tự submit hidden form
   tới action URL của chính authenticator, kèm qr_session_id

7. QrLoginAuthenticator.action() nhận qr_session_id, tra ra userId đã approve, gọi
   context.setUser(user) + context.success() → Keycloak issue token theo flow chuẩn
   của client đang đăng nhập, không có bước mint token thủ công nào trong SPI này
```

Endpoint `check` chỉ đọc trạng thái, không issue token và không xoá session — việc issue
token luôn đi qua `AuthenticationFlowContext` chuẩn ở bước 7.

Khi QR hết hạn (đếm ngược về 0, hoặc `check` trả `status=expired`) mà chưa được quét/approve,
trang login tự gọi lại `POST /qr-login/start` để lấy `session_id` mới, vẽ lại mã QR và tiếp
tục poll — người dùng không cần tự F5 trang.

### REST API (`/realms/{realm}/qr-login/*`)

| Method | Path       | Caller          | Auth              | Mô tả |
|--------|------------|-----------------|-------------------|-------|
| POST   | `/start`   | Trang login     | —                 | Tạo `session_id` mới (dùng khi cần tạo QR ngoài luồng authenticator mặc định) |
| POST   | `/scan`    | App di động     | Bearer access_token | Ghi nhận người dùng đã quét, chuyển `pending` → `scanned` |
| POST   | `/approve` | App di động     | Bearer access_token | Xác nhận đăng nhập, chuyển `scanned` → `approved`; chỉ chấp nhận từ đúng `userId` đã scan |
| POST   | `/cancel`  | App di động     | Bearer access_token | Huỷ xác nhận, trả `scanned` về `pending` |
| POST   | `/check`   | Trang login     | —                 | Trả trạng thái hiện tại (`pending`/`scanned`/`approved`/`expired`) |

`access_token` được verify tại chỗ bằng public key của chính realm
(`TokenIntrospection`), không cần round-trip tới JWKS endpoint.

Session QR hết hạn sau `QrLoginSessionStore.TTL_SECONDS` (180s).

## Cấu trúc project

```
src/main/java/com/snp/keycloak/qrlogin/
  QrLoginAuthenticator.java          # bước xác thực ALTERNATIVE trong Browser Flow
  QrLoginAuthenticatorFactory.java   # đăng ký authenticator, id "qr-login-authenticator"
  QrLoginResourceProvider.java       # REST endpoint /realms/{realm}/qr-login/*
  QrLoginResourceProviderFactory.java# đăng ký REST provider, id "qr-login"
  QrLoginSessionStore.java           # lưu trạng thái phiên QR trong bộ nhớ (in-process)
  TokenIntrospection.java            # verify access_token bằng public key của realm

src/main/resources/
  META-INF/services/                 # đăng ký 2 factory ở trên theo Java ServiceLoader
  META-INF/keycloak-themes.json      # khai báo theme "qr-login"
  theme/qr-login/login/
    qr-login.ftl                     # trang hiện mã QR + JS polling
    theme.properties
    messages/messages_{vi,en}.properties
    resources/js/qrcode.min.js       # thư viện vẽ QR client-side
```

## Build

Yêu cầu JDK 17 (khớp `maven.compiler.release`) và Maven.

```bash
mvn clean package
```

JAR xuất ra `target/qr-login-spi.jar` (do `<finalName>qr-login-spi</finalName>`).

Version `keycloak.version` trong `pom.xml` phải khớp image Keycloak đang chạy (hiện là
`26.1.4`, xem `docker-compose.yml`) — mismatch có thể gây lỗi ClassNotFound/NoSuchMethod lúc
Keycloak load provider.

## Triển khai

Repo gốc (`../docker-compose.yml`) đã mount sẵn:

```yaml
volumes:
  - ./keycloak-spi-qr-login/target/qr-login-spi.jar:/opt/keycloak/providers/qr-login-spi.jar
```

Sau khi build JAR mới, restart container `keycloak` để Keycloak nạp lại provider:

```bash
docker compose restart keycloak
```

### Bật flow trong Keycloak Admin Console

1. Vào realm cần dùng → **Authentication** → duplicate flow **Browser** (hoặc dùng flow đã
   có sẵn `Browser with QR` nếu realm import từ `keycloak-config/`).
2. Thêm bước **Đăng nhập bằng QR** (`qr-login-authenticator`) song song với **Username
   Password Form**, cả hai để **ALTERNATIVE**.
3. Bind flow này làm **Browser Flow** cho client cần test.
4. Client gọi flow này (vd `test-qr-web-2`) sẽ thấy nút "Try another way" trên trang login
   mặc định, dẫn tới lựa chọn "Đăng nhập bằng QR".

## Giới hạn hiện tại

- `QrLoginSessionStore` là `ConcurrentHashMap` trong tiến trình Keycloak — mất khi restart,
  và không chia sẻ giữa nhiều node nếu chạy cluster (cần chuyển sang Infinispan cache).
- Không có rate-limit riêng cho các endpoint `/qr-login/*`.

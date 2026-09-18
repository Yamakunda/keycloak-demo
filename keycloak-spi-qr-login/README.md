# qr-login-spi

Custom Keycloak SPI cho đăng nhập bằng QR cross-device: quét mã trên trang login bằng app di
động đã đăng nhập sẵn, xác nhận trên điện thoại (biometric/mật khẩu), trang trên máy tính tự
đăng nhập.

Một theme duy nhất `qr-login`. UI chọn bằng Browser Flow đang dùng, không cần đổi theme:

- **Browser with QR** — QR ẩn sau "Try another way".
- **Browser with QR Combined** — form password và QR hiện song song trên cùng màn hình.

## REST API (`/realms/{realm}/qr-login/*`)

| Method | Path       | Caller      | Auth                 | Mô tả |
|--------|------------|-------------|-----------------------|-------|
| POST   | `/start`   | Trang login | —                     | Tạo `session_id` mới |
| POST   | `/scan`    | App di động | Bearer access_token   | `pending` → `scanned` |
| POST   | `/approve` | App di động | Bearer access_token   | `scanned` → `approved` |
| POST   | `/cancel`  | App di động | Bearer access_token   | Huỷ xác nhận, về `pending` |
| POST   | `/check`   | Trang login | —                     | Trả trạng thái hiện tại |

Session QR hết hạn sau `QrLoginSessionStore.TTL_SECONDS` (180s), hết hạn thì trang tự gọi lại
`/start` để lấy QR mới, không cần F5.

## Build

```bash
mvn clean package
```

JAR xuất ra `target/qr-login-spi.jar`. `keycloak.version` trong `pom.xml` phải khớp image
Keycloak đang chạy (`docker-compose.yml`).

## Triển khai

```bash
docker compose restart keycloak
```

JAR đã được mount sẵn qua volume trong `../docker-compose.yml`, chỉ cần build lại rồi restart.

## Bật trong Admin Console

**Bước 0 (một lần duy nhất):** Realm Settings → Themes → Login theme = `qr-login`.

**Cách 1 — QR ẩn sau "Try another way":**
1. Authentication → duplicate flow Browser (hoặc dùng `Browser with QR` có sẵn).
2. Thêm bước **Đăng nhập bằng QR** (`qr-login-authenticator`) song song Username Password Form, cả hai ALTERNATIVE.
3. Bind flow làm Browser Flow cho realm/client cần dùng.

**Cách 2 — Form password + QR song song:**
1. Authentication → duplicate flow Browser (hoặc dùng `Browser with QR Combined` có sẵn).
2. Xoá/Disable bước Username Password Form mặc định.
3. Thêm bước **Username Password Form with QR** (`qr-login-combined-authenticator`), để REQUIRED.
4. Bind flow làm Browser Flow cho realm/client cần dùng.

Đổi qua lại giữa 2 cách chỉ cần đổi Browser Flow binding, không cần đổi lại Login Theme.

## Giới hạn

- Session QR lưu trong bộ nhớ (`ConcurrentHashMap`) — mất khi restart, không chia sẻ giữa nhiều node nếu chạy cluster.
- Không có rate-limit riêng cho các endpoint `/qr-login/*`.

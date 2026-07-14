# bookstore-saml-api

Một **SAML 2.0 Service Provider** tối giản: backend Express xác thực qua
**Keycloak** đóng vai trò SAML Identity Provider, giữ session phía server
(cookie httpOnly), và cung cấp JSON API sách cho
[bookstore-saml-fe](../bookstore-saml-fe/) (port 3032).

Khác với các demo bookstore OAuth/OIDC trong repo này, ở đây không có token —
Keycloak POST một assertion XML đã ký vào endpoint ACS của app. Đó là lý do
phần SAML bắt buộc phải nằm ở backend: SPA thuần không bao giờ nhận hay verify
được assertion.

## Luồng đăng nhập

1. Browser vào `GET /login` (redirect nguyên trang từ FE) → app tạo
   `AuthnRequest` và redirect sang endpoint SAML của Keycloak
   (`/realms/test/protocol/saml`).
2. User đăng nhập trên Keycloak (ví dụ `test` / `123456`).
3. Keycloak POST một **SAMLResponse đã ký** vào `POST /saml/acs`
   (Assertion Consumer Service).
4. App verify chữ ký bằng certificate ký của realm (tự fetch lúc khởi động từ
   IdP metadata tại `/realms/test/protocol/saml/descriptor`), tạo session,
   set cookie httpOnly, rồi redirect browser về FE (`:3032`).
5. FE gọi JSON API với `credentials: "include"`; roles nằm trong assertion
   dưới dạng attribute `Role` (do `saml-role-list-mapper` gắn vào).

## Phân quyền

| Hành động | Yêu cầu |
|---|---|
| `GET /api/me`, `GET /api/books` | chỉ cần session đã đăng nhập |
| `POST /api/books` | realm role `book-admin` |
| `DELETE /api/books/:id` | realm role `book-admin` |

Realm import gán sẵn role `book-admin` cho user `test`. Tạo thêm một user
không có role này để thấy giao diện read-only / lỗi 403.

## Endpoints

| Endpoint | Công dụng |
|---|---|
| `GET /login` | Bắt đầu SP-initiated SSO (browser chuyển trang, không phải fetch) |
| `POST /saml/acs` | Assertion Consumer Service (Keycloak POST assertion vào đây) |
| `GET /metadata` | SP metadata XML (import được vào Keycloak) |
| `GET /logout` | SP-initiated Single Logout (gửi `LogoutRequest` sang Keycloak) |
| `GET/POST /saml/logout/callback` | Nơi Keycloak trả về sau khi logout |
| `GET /api/me` | User hiện tại + roles + attribute thô từ assertion |
| `GET/POST/DELETE /api/books…` | CRUD sách (JSON) |

## Các bước cấu hình Keycloak

Toàn bộ cấu hình dưới đây đã được mã hóa sẵn trong
[keycloak-config/test-realm.json](../keycloak-config/test-realm.json), nên
Keycloak mới tinh sẽ tự có đủ khi chạy `--import-realm`. Các bước được ghi lại
ở đây để bạn có thể làm lại bằng tay trên Admin Console (hoặc hiểu file JSON
đang khai gì). Mỗi bước được đánh dấu **bắt buộc** (thiếu là login hỏng) hoặc
**tùy chọn**.

### 1. Tạo client SAML — bắt buộc

**Clients → Create client**:

| Trường | Giá trị | Lý do |
|---|---|---|
| Client type | `SAML` | thay vì OpenID Connect |
| Client ID | `bookstore-saml` | phải trùng entityID của SP (`SP_ENTITY_ID` trong `.env`). Keycloak đối chiếu `<Issuer>` của AuthnRequest gửi đến với giá trị này |

Cách nhanh hơn: **Clients → Import client** rồi dán URL metadata của SP
(`http://localhost:3033/metadata`) — Keycloak tự điền ACS URL, logout URL và
NameID format từ file XML.

### 2. Access settings — bắt buộc

- **Valid redirect URIs**: `http://localhost:3033/*`. Keycloak từ chối POST
  assertion về bất kỳ URL nào không nằm trong danh sách này. Nên để càng hẹp
  càng tốt.

### 3. Cấu hình chữ ký — bắt buộc

Tab **Settings → Signature and Encryption**:

| Switch | Giá trị | Ghi chú |
|---|---|---|
| Sign documents | ON | mặc định; ký lớp SAMLResponse bên ngoài |
| Sign assertions | **ON** | ⚠ mặc định là OFF — SP này khai `wantAssertionsSigned: true`, để nguyên OFF là lỗi "Invalid signature" |
| Client signature required | **ON** | mặc định; SP ký AuthnRequest/LogoutRequest bằng key trong [certs/sp-key.pem](certs/sp-key.pem), Keycloak verify bằng cert đăng ký ở tab **Keys** của client. Xem ghi chú bên dưới |
| Signature algorithm | `RSA_SHA256` | mặc định |

Cert của SP phải được khai cho client: tab **Keys → Signing keys config →
Import key** (dán `certs/sp-cert.pem`), hoặc để Keycloak tự lấy khi import
client từ SP metadata (`/metadata` đã kèm cert). Keypair demo được tạo bằng:

```bash
openssl req -x509 -newkey rsa:2048 -nodes \
  -keyout certs/sp-key.pem -out certs/sp-cert.pem \
  -days 3650 -subj "/CN=bookstore-saml-test"
```

> **Client signature có quan trọng không?** Nó bảo vệ chiều ngược lại
> (SP → IdP): chứng minh AuthnRequest/LogoutRequest thật sự do SP tạo ra.
> Tắt đi thì attacker chủ yếu làm được các trò phiền toái (giả LogoutRequest,
> login CSRF) — bản thân assertion vẫn an toàn vì Keycloak chỉ POST về các
> redirect URI đã đăng ký. Vì vậy demo trước đây tắt switch này (SP khỏi cần
> keypair); giờ bật lại đúng khuyến nghị production, nhất là khi dùng Single
> Logout. Nếu tắt switch, SP vẫn chạy bình thường — Keycloak chỉ bỏ qua
> chữ ký chứ không từ chối request có ký.

### 4. SAML capabilities — mặc định đã ổn

- **Name ID format**: `username` (mặc định) → SP đọc thành `req.user.nameID`.
  Bật *Force name ID format* để Keycloak bỏ qua format mà SP đề nghị trong
  AuthnRequest.
- **Force POST binding**: ON (mặc định) — response đi bằng form HTML tự
  submit, đúng cái endpoint ACS của SP chờ nhận.
- **Include AuthnStatement**: ON (mặc định) — đưa `sessionIndex` vào
  assertion; cần cho Single Logout sau này.

### 5. Fine-grained endpoints — tùy chọn với SP-initiated login

Tab **Advanced**:

- *Assertion Consumer Service POST Binding URL*: `http://localhost:3033/saml/acs`.
  Ở đây là tùy chọn vì AuthnRequest đã tự khai ACS URL; nhưng thành **bắt
  buộc** nếu muốn IdP-initiated login (user bấm vào app từ trong Keycloak,
  không có AuthnRequest nào trước đó).
- *Logout Service Redirect Binding URL*: `http://localhost:3033/saml/logout/callback`.
  Chỉ cần cho Single Logout.

### 6. Mappers — bắt buộc cho authorization, không cần cho login

Không có mapper thì assertion chỉ chứa mỗi NameID; login vẫn thành công nhưng
user nào cũng chỉ read-only. Tab **Client scopes → bookstore-saml-dedicated →
Add mapper**:

- **Role list** (`saml-role-list-mapper`): đẩy realm roles của user vào
  attribute tên `Role`. Bật **Single Role Attribute** để tất cả roles nằm
  trong một attribute nhiều value (hàm `rolesOf()` của API xử lý được cả hai
  dạng).
- **User property** (`saml-user-property-mapper`) cho `email`, `firstName`,
  `lastName` — thuần cosmetic; hiện trong mục dump attribute trên FE.

### 7. Roles — bắt buộc cho authorization

- **Realm roles → Create role**: `book-admin` (và `book-viewer`).
- **Users → test → Role mapping → Assign role**: `book-admin`.

Keycloak chỉ *vận chuyển* role trong assertion; còn `book-admin` được làm gì
là logic của app này ([src/routes/books.js](src/routes/books.js)).

### 8. SP cần gì từ Keycloak — bắt buộc

Chỉ hai thứ, đều là thông tin công khai:

- **certificate ký** của realm, công bố trong IdP metadata tại
  `http://localhost:8080/realms/test/protocol/saml/descriptor` (app này tự
  fetch lúc khởi động — [src/saml.js](src/saml.js));
- endpoint SAML `http://localhost:8080/realms/test/protocol/saml`, dùng chung
  cho cả login lẫn logout.

> Realm JSON chỉ được import khi realm chưa tồn tại. Nếu container Keycloak
> của bạn đã chạy từ trước khi client này được thêm vào, hoặc recreate nó
> (`docker compose up -d --force-recreate keycloak` — re-import realm nhưng
> mất các thay đổi cấu hình tay như LDAP federation), hoặc tạo client/role
> bằng tay theo các bước trên, hoặc qua `kcadm.sh`.

## Chạy

```bash
# qua docker compose (từ thư mục gốc repo)
docker compose up -d --build bookstore-saml-api bookstore-saml-fe

# hoặc chạy local
cp .env.example .env
npm install
npm start
```

API ở http://localhost:3033, UI ở http://localhost:3032 (`test` / `123456`).

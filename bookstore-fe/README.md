# Keycloak SPA Demo (frontend thuần)

React SPA chạy ở **http://localhost:3002**, đăng nhập qua Keycloak bằng
**Authorization Code flow** — toàn bộ luồng OAuth2 chạy ngay trên browser,
không có backend trung gian.

## Luồng hoạt động

1. Bấm **Đăng nhập với Keycloak** (route `/login`) → app redirect sang
   `GET /realms/poc-realm/protocol/openid-connect/auth?response_type=code&state=...`.
2. Keycloak xác thực xong redirect về route `/callback?code=...&state=...`.
3. `CallbackPage` `fetch POST /token` với `code` + `client_secret`
   (confidential client — Client authentication = On)
   → nhận `access_token`, `id_token`, `refresh_token` rồi chuyển về `/`.
4. **Access token lưu vào cookie `access_token`** (max-age = expires_in) —
   xem ở DevTools → Application → Cookies.
5. Token trong cookie được dùng để gọi API, ví dụ `GET /userinfo` với
   `Authorization: Bearer <token>`.

Client tương ứng trong Keycloak: `frontend-poc-client`
(confidential client, secret `frontend-poc-secret`, webOrigins `http://localhost:3002`
để cho phép CORS) — khai báo trong `../keycloak-config/realm-poc.json`.
Secret đặt trong `src/config/keycloak.js` hoặc biến môi trường `REACT_APP_CLIENT_SECRET`.

> ⚠️ Secret nằm trong bundle JS nên ai mở DevTools cũng đọc được — chấp nhận
> được cho demo học flow; production thì token exchange phải nằm ở backend.

## Cấu trúc thư mục

Code viết bằng **TypeScript** (strict mode, `tsconfig.json` ở gốc project).

```
src/
├── config/keycloak.ts         # URL Keycloak, realm, client_id, secret, redirect_uri
├── utils/
│   ├── cookie.ts              # set/get/delete cookie
│   ├── random.ts              # sinh chuỗi ngẫu nhiên cho tham số state
│   └── jwt.ts                 # decode JWT payload + interface JwtClaims
├── services/authService.ts    # mọi call tới Keycloak API: login, exchangeCode,
│                              # refreshTokens, fetchUserinfo, logout
│                              # (interface TokenResponse, UserInfo)
├── context/AuthContext.tsx    # AuthProvider + useAuth — state đăng nhập dùng chung
├── components/
│   ├── ProtectedRoute.tsx     # chưa đăng nhập → redirect /login
│   ├── Navbar.tsx
│   └── Spinner.tsx
├── pages/
│   ├── LoginPage.tsx          # /login
│   ├── CallbackPage.tsx       # /callback — nhận ?code=, đổi token, về /
│   └── BookstorePage.tsx      # / (protected) — trang bán sách placeholder
└── App.tsx                    # BrowserRouter + Routes
```

Routes:

| Route       | Trang           | Ghi chú                                        |
|-------------|-----------------|------------------------------------------------|
| `/`         | BookstorePage   | Protected — chưa có token thì đẩy về `/login`  |
| `/login`    | LoginPage       | Đã đăng nhập thì đẩy về `/`                    |
| `/callback` | CallbackPage    | Redirect URI đăng ký với Keycloak              |
| `*`         | —               | Về `/`                                         |

## Chạy

```bash
# 1. Keycloak phải đang chạy (từ thư mục gốc):
docker compose up -d keycloak
#    Nếu realm poc-realm đã import từ trước (chưa có client frontend-poc-client),
#    recreate container để import lại:
docker compose up -d --force-recreate keycloak

# 2. Chạy frontend:
npm install
npm start          # mở http://localhost:3002 (PORT đặt trong .env)
```

Đăng nhập bằng user demo: `testuser` / `password123` hoặc `adminuser` / `admin123`.

## Ghi chú demo

- Cookie `access_token` **không** đặt `HttpOnly` (cookie do JS tạo thì không thể) —
  đây là chủ đích để demo đọc/ghi token từ frontend. Production nên để backend
  quản lý token hoặc giữ token trong bộ nhớ.
- `refresh_token` + `id_token` giữ trong `sessionStorage`; nút **Refresh token**
  đổi refresh token lấy access token mới và cập nhật lại cookie.
- Đăng xuất gọi RP-Initiated Logout (`/logout?id_token_hint=...`) để kết thúc
  luôn SSO session phía Keycloak.
# test-keycloak-fe

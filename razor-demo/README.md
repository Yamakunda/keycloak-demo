# RazorKeycloakDemo — ASP.NET Core Razor Pages + Keycloak OIDC

Demo luồng đăng nhập/đăng xuất qua Keycloak dùng **OpenID Connect Authorization Code Flow + PKCE** với middleware `AddOpenIdConnect` (không dùng keycloak-js).

## Kiến trúc

- **Realm:** `test` (realm test sẵn có trên Keycloak — client `razor-demo-client` được tạo qua Admin API)
- **Client:** `razor-demo-client` (confidential, secret: `razor-demo-secret`)
- **User test:** `test` (user sẵn có trong realm `test`)
- Cookie auth: `HttpOnly`, `SameSite=Lax`, sliding 30 phút
- `SaveTokens = true` — xem access/id/refresh token ở trang Profile
- Console log mỗi lần token được cấp (`TOKEN ISSUED` / `TOKEN VALIDATED` / `SINGLE LOGOUT`)

## Chạy local (dotnet run)

Yêu cầu Keycloak đang chạy: `docker compose up -d keycloak` (từ thư mục cha).

```bash
cd razor-demo
# Toàn bộ config Keycloak (ServerUrl/Realm/ClientId/ClientSecret) đọc từ .env
cp .env.example .env   # rồi điền Keycloak__ClientSecret
dotnet run --launch-profile https   # https://localhost:7200 (hoặc http: cổng 5200)
```

## Chạy bằng Docker

```bash
docker compose up -d --build razor-app   # từ thư mục cha
# App: http://localhost:5100 — compose nạp env qua env_file: ./razor-demo/.env
```

## Luồng demo

1. Vào `/` → thấy nút **Login** (trang public).
2. Login → redirect sang trang login của Keycloak → nhập `test` (user sẵn có trong realm `test`) → quay lại app với session cookie; tên + email hiện trên trang chủ. Console in `TOKEN ISSUED` / `TOKEN VALIDATED`.
3. `/Profile` (yêu cầu `[Authorize]`) → list toàn bộ claims (`sub`, `email`, `name`, `preferred_username`…) và tokens.
4. **Logout** → xoá cookie local **và** gọi end-session endpoint của Keycloak (single logout) → quay về `/`. Vào lại `/Profile` sẽ bị bắt login lại.

## Endpoint kỹ thuật

| Path | Vai trò |
|---|---|
| `/signin-oidc` | Redirect URI nhận authorization code (form_post) |
| `/signout-callback-oidc` | Post-logout redirect từ Keycloak |
| `/Account/Login` | Challenge OIDC scheme |
| `/Account/Logout` | SignOut cả cookie + OIDC scheme (POST, có antiforgery) |

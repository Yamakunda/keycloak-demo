# Bookstore API

REST API nhà sách (Express, port **3003**) — mọi endpoint sách đều yêu cầu
access token Keycloak hợp lệ, verify bằng **token introspection**
(server-to-server, dùng client_secret an toàn).

## Cấu trúc

```
src/
├── config.js            # port, origin frontend, Keycloak realm/client/secret
├── middleware/auth.js   # requireToken — introspect token với Keycloak
├── data/books.js        # kho sách — lưu ở file data/books.json
├── routes/books.js      # GET / POST / PUT / DELETE + validation
└── server.js            # Express app + CORS
data/books.json          # dữ liệu sách (tự tạo lần chạy đầu, sống qua restart)
```

Lưu trữ: sách nằm trong `data/books.json`. Lần chạy đầu chưa có file thì
tự seed 5 cuốn mẫu; mỗi lần tạo/sửa/xóa là ghi lại cả file. Muốn reset về
dữ liệu mẫu: xóa file `data/books.json` rồi restart.

## Endpoints

| Method | Path             | Mô tả                                  |
|--------|------------------|----------------------------------------|
| GET    | `/health`        | Health check — không cần token         |
| GET    | `/api/books`     | Danh sách sách                         |
| GET    | `/api/books/:id` | Xem một cuốn                           |
| POST   | `/api/books`     | Tạo sách `{ title, author, price, genre?, cover? }` |
| PUT    | `/api/books/:id` | Update — gửi trường nào sửa trường đó  |
| DELETE | `/api/books/:id` | Xóa sách — trả 204                     |

Token lấy từ header `Authorization: Bearer <token>` **hoặc** cookie
`access_token` (cookie không phân biệt port nên cookie do frontend
localhost:3002 tạo cũng gửi được sang localhost:3003 khi fetch với
`credentials: "include"`).

## Chạy

```bash
npm install
npm start        # http://localhost:3003
```

Cấu hình qua biến môi trường (mặc định trong `src/config.js`):
`PORT`, `FRONTEND_ORIGIN`, `KEYCLOAK_URL`, `REALM`, `CLIENT_ID`, `CLIENT_SECRET`.

## Test nhanh

Đăng nhập frontend (http://localhost:3002) → DevTools → Application →
Cookies → copy giá trị `access_token`, rồi:

```bash
# Xem sách
curl http://localhost:3003/api/books -H "Authorization: Bearer <token>"

# Tạo sách
curl -X POST http://localhost:3003/api/books \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"title":"Sapiens","author":"Yuval Noah Harari","price":210000,"genre":"History"}'

# Update giá sách id=6
curl -X PUT http://localhost:3003/api/books/6 \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"price":199000}'

# Không có token → 401
curl http://localhost:3003/api/books
```
# test-keycloak-be

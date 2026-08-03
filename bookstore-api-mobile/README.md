# Bookstore API — Mobile

REST API nhà sách (Express, port **3043**) dành riêng cho app Android
`bookstore-android`. Mọi endpoint sách yêu cầu access token Keycloak hợp lệ,
verify **cục bộ bằng chữ ký JWT** qua JWKS của Keycloak (không cần
`client_secret` — phù hợp public client dùng Authorization Code + PKCE
trên mobile).

## Cấu trúc

```
src/
├── config.js            # port, Keycloak issuer/JWKS URI
├── middleware/auth.js   # requireToken — verify JWT bằng public key (jwks-rsa)
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
|--------|------------------|-----------------------------------------|
| GET    | `/health`        | Health check — không cần token         |
| GET    | `/api/books`     | Danh sách sách                         |
| GET    | `/api/books/:id` | Xem một cuốn                           |
| POST   | `/api/books`     | Tạo sách `{ title, author, price, genre?, cover? }` |
| PUT    | `/api/books/:id` | Update — gửi trường nào sửa trường đó  |
| DELETE | `/api/books/:id` | Xóa sách — trả 204                     |

Token lấy từ header `Authorization: Bearer <token>` — app Android tự đổi
authorization code lấy token qua AppAuth (PKCE) rồi gắn Bearer khi gọi API.

## Chạy

```bash
npm install
npm start        # http://localhost:3043
```

Cấu hình qua biến môi trường (`.env`, xem `.env.example`):
`PORT`, `KEYCLOAK_URL`, `REALM`, `CLIENT_ID`.

## Test nhanh

Lấy access token từ app Android (log ra ở màn hình debug) hoặc qua
`curl-tests.txt`, rồi:

```bash
# Xem sách
curl http://localhost:3043/api/books -H "Authorization: Bearer <token>"

# Tạo sách
curl -X POST http://localhost:3043/api/books \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"title":"Sapiens","author":"Yuval Noah Harari","price":210000,"genre":"History"}'

# Không có token → 401
curl http://localhost:3043/api/books
```

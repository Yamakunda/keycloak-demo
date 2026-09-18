const express = require("express");
const cors = require("cors");
const cookieParser = require("cookie-parser");
const config = require("./config");
const { requireToken } = require("./middleware/auth");
const booksRouter = require("./routes/books");
const authRouter = require("./routes/auth");

const app = express();
// CORS cho frontend (bookstore-fe-qr-2); credentials: true để browser gửi kèm cookie
app.use(cors({ origin: config.FRONTEND_ORIGIN, credentials: true }));
app.use(express.json());
app.use(cookieParser());

// Health check — không cần token
app.get("/health", (req, res) => res.json({ status: "ok" }));

// Login/callback/refresh/logout — đổi code lấy token ở đây, không cần token trước đó
app.use("/api/auth", authRouter);

// Proxy sách — yêu cầu access token hợp lệ (Keycloak introspection), gọi hộ
// bookstore-api-mobile bằng Bearer header phía server
app.use("/api/books", requireToken, booksRouter);

app.use((req, res) => res.status(404).json({ error: "not_found" }));

app.listen(config.PORT, () =>
  console.log(`[Bookstore API QR-2] Running at http://localhost:${config.PORT} (Keycloak: ${config.KC_BASE})`)
);

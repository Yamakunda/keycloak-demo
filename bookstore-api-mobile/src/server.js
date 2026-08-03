const express = require("express");
const cors = require("cors");
const config = require("./config");
const { requireToken } = require("./middleware/auth");
const booksRouter = require("./routes/books");

const app = express();
// App Android gọi trực tiếp bằng Bearer token, không có browser origin cố định
// (Custom Tab redirect quay lại app qua deep link) nên mở CORS rộng cho demo.
app.use(cors());
app.use(express.json());

app.get("/health", (req, res) => res.json({ status: "ok" }));

// Toàn bộ API sách yêu cầu Bearer access token hợp lệ (verify chữ ký JWT qua JWKS)
app.use("/api/books", requireToken, booksRouter);

app.use((req, res) => res.status(404).json({ error: "not_found" }));

app.listen(config.PORT, () =>
  console.log(`[Bookstore API Mobile] Running at http://localhost:${config.PORT} (Keycloak: ${config.ISSUER})`)
);

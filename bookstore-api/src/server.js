const express = require("express");
const cors = require("cors");
const dotenv = require("dotenv");
const config = require("./config");
const { requireToken } = require("./middleware/auth");
const booksRouter = require("./routes/books");

const app = express();
dotenv.config();
// CORS cho frontend (localhost:3002); credentials: true để browser gửi kèm cookie access_token
app.use(cors({ origin: config.FRONTEND_ORIGIN, credentials: true }));
app.use(express.json());

// Health check — không cần token
app.get("/health", (req, res) => res.json({ status: "ok" }));

// Toàn bộ API sách yêu cầu access token hợp lệ (Keycloak introspection)
app.use("/api/books", requireToken, booksRouter);

app.use((req, res) => res.status(404).json({ error: "not_found" }));

app.listen(config.PORT, () =>
  console.log(`[Bookstore API] Running at http://localhost:${config.PORT} (Keycloak: ${config.KC_BASE})`)
);

const crypto = require("crypto");
const express = require("express");
const { requireToken } = require("../middleware/auth");

const router = express.Router();

// Session QR tự-approve (không dùng Device Authorization Grant của Keycloak) — app đã
// đăng nhập sẵn bằng Authorization Code + PKCE tự gửi access_token của nó lên đây thay vì
// mở Custom Tab, nên không cần bước bấm "Xác nhận" trên trình duyệt.
// Lưu in-memory: đủ cho demo 1 instance, KHÔNG dùng cho production nhiều instance.
const SESSION_TTL_MS = 2 * 60 * 1000;
const sessions = new Map();

function cleanupExpired() {
  const now = Date.now();
  for (const [id, session] of sessions) {
    if (session.expiresAt < now) sessions.delete(id);
  }
}

// POST /qr/start — web gọi khi hiện mã QR
router.post("/start", (req, res) => {
  cleanupExpired();
  const sessionId = crypto.randomBytes(16).toString("hex");
  sessions.set(sessionId, {
    status: "pending",
    createdAt: Date.now(),
    expiresAt: Date.now() + SESSION_TTL_MS,
    token: null,
  });
  res.json({ session_id: sessionId, expires_in: SESSION_TTL_MS / 1000 });
});

// POST /qr/approve — app gọi sau khi quét QR, kèm access_token của app trong header
router.post("/approve", requireToken, (req, res) => {
  const { session_id, access_token, refresh_token, expires_in, token_type, scope } = req.body;
  const session = sessions.get(session_id);

  if (!session) {
    return res.status(404).json({ error: "not_found", message: "Phiên QR không tồn tại hoặc đã hết hạn" });
  }
  if (session.status !== "pending") {
    return res.status(409).json({ error: "already_used", message: "Phiên QR đã được xử lý" });
  }
  if (!access_token) {
    return res.status(400).json({ error: "invalid_request", message: "Thiếu access_token" });
  }

  session.status = "approved";
  session.token = { access_token, refresh_token, expires_in, token_type, scope };
  session.username = req.tokenInfo.preferred_username || req.tokenInfo.sub;
  res.json({ status: "approved" });
});

// POST /qr/poll — web gọi lặp lại để chờ app approve
router.post("/poll", (req, res) => {
  const { session_id } = req.body;
  const session = sessions.get(session_id);

  if (!session) {
    return res.status(404).json({ status: "expired" });
  }
  if (session.expiresAt < Date.now()) {
    sessions.delete(session_id);
    return res.status(404).json({ status: "expired" });
  }
  if (session.status === "pending") {
    return res.json({ status: "pending" });
  }

  const { token, username } = session;
  sessions.delete(session_id); // dùng 1 lần, tránh replay lại token đã phát
  res.json({ status: "approved", token, username });
});

module.exports = router;

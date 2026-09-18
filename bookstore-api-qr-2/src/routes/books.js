const express = require("express");
const config = require("../config");

const router = express.Router();

// GET /api/books — proxy sang bookstore-api-mobile bằng Bearer header ở phía server.
// FE không còn giữ access_token (nằm trong cookie httpOnly) nên không thể tự gọi thẳng
// bookstore-api-mobile — cookie của bookstore-api-qr-2 và bookstore-api-mobile là 2
// domain/port khác nhau, không chia sẻ session.
router.get("/", async (req, res) => {
  try {
    const upstream = await fetch(`${config.BOOKS_API_URL}/api/books`, {
      headers: { Authorization: `Bearer ${req.accessToken}` },
    });

    const body = await upstream.text();
    res.status(upstream.status);
    res.set("Content-Type", upstream.headers.get("content-type") || "application/json");
    res.send(body);
  } catch (err) {
    res.status(502).json({ error: "books_api_unreachable", message: err.message });
  }
});

module.exports = router;

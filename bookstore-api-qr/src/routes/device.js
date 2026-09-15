const express = require("express");
const config = require("../config");

const router = express.Router();

function basicAuthHeader() {
  const raw = `${config.CLIENT_ID}:${config.CLIENT_SECRET}`;
  return `Basic ${Buffer.from(raw).toString("base64")}`;
}

// FE gọi trước để lấy user_code/QR — client_secret không bao giờ rời khỏi backend.
router.post("/start", async (req, res) => {
  const response = await fetch(config.DEVICE_AUTH_ENDPOINT, {
    method: "POST",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
      Authorization: basicAuthHeader(),
    },
    body: new URLSearchParams({ client_id: config.CLIENT_ID }),
  });

  const data = await response.json();
  if (!response.ok) {
    return res.status(response.status).json(data);
  }

  res.json(data);
});

// FE poll endpoint này theo "interval" trả về ở /start cho tới khi có token hoặc hết hạn.
router.post("/poll", async (req, res) => {
  const { device_code } = req.body;
  if (!device_code) {
    return res.status(400).json({ error: "invalid_request", error_description: "Missing device_code" });
  }

  const response = await fetch(config.TOKEN_ENDPOINT, {
    method: "POST",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
      Authorization: basicAuthHeader(),
    },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:device_code",
      device_code,
    }),
  });

  const data = await response.json();
  // Keycloak trả 400 + error=authorization_pending/slow_down trong lúc chờ — đây là trạng thái
  // bình thường của polling, không phải lỗi thật, nên luôn forward nguyên trạng cho FE tự xử lý.
  res.status(response.status).json(data);
});

module.exports = router;

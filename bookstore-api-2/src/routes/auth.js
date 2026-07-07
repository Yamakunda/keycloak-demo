const crypto = require("crypto");
const express = require("express");
const config = require("../config");

const router = express.Router();

const ACCESS_COOKIE = "access_token";
const REFRESH_COOKIE = "refresh_token";
const STATE_COOKIE = "oauth_state";

const sessionCookieOpts = (maxAgeSec) => ({
  httpOnly: true,
  sameSite: "lax",
  secure: false, // demo chạy http://localhost — bật true khi deploy https
  path: "/",
  maxAge: maxAgeSec * 1000,
});

function setSessionCookies(res, tokens) {
  res.cookie(ACCESS_COOKIE, tokens.access_token, sessionCookieOpts(tokens.expires_in));
  if (tokens.refresh_token) {
    res.cookie(REFRESH_COOKIE, tokens.refresh_token, sessionCookieOpts(tokens.refresh_expires_in || 30 * 24 * 3600));
  }
}

// GET /api/auth/login — FE chỉ cần window.location.href = "{API_URL}/api/auth/login".
// Backend sinh state (chống CSRF, lưu cookie httpOnly ngắn hạn) rồi redirect thẳng sang Keycloak.
router.get("/login", (req, res) => {
  const state = crypto.randomBytes(24).toString("base64url");
  res.cookie(STATE_COOKIE, state, sessionCookieOpts(300));

  const params = new URLSearchParams({
    response_type: "code",
    client_id: config.CLIENT_ID,
    redirect_uri: config.REDIRECT_URI,
    scope: "openid profile email",
    state,
  });

  res.redirect(`${config.KC_BASE}/auth?${params}`);
});

// GET /api/auth/callback — Keycloak redirect thẳng vào đây (redirect_uri = backend, không phải FE).
// Backend đổi code lấy token (client_secret không rời server), set cookie httpOnly,
// rồi redirect browser về trang chủ SPA — FE không bao giờ thấy code/token thô.
router.get("/callback", async (req, res) => {
  const { code, state, error, error_description: errorDescription } = req.query;
  const expectedState = req.cookies?.[STATE_COOKIE];
  res.clearCookie(STATE_COOKIE, { path: "/" });

  const failFrontend = (message) =>
    res.redirect(`${config.FRONTEND_ORIGIN}/login?error=${encodeURIComponent(message)}`);

  if (error) return failFrontend(errorDescription || error);
  if (!code) return failFrontend("Thiếu authorization code");
  if (!state || state !== expectedState) return failFrontend("state không khớp — có thể là CSRF, hủy đăng nhập");

  try {
    const tokenRes = await fetch(`${config.KC_BASE}/token`, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        grant_type: "authorization_code",
        client_id: config.CLIENT_ID,
        client_secret: config.CLIENT_SECRET,
        redirect_uri: config.REDIRECT_URI,
        code,
      }),
    });

    const tokens = await tokenRes.json();
    if (!tokenRes.ok) return failFrontend(tokens.error_description || tokens.error || "Token exchange failed");

    setSessionCookies(res, tokens);
    res.redirect(config.FRONTEND_ORIGIN);
  } catch (err) {
    failFrontend(err.message);
  }
});

// POST /api/auth/refresh — dùng refresh_token trong cookie httpOnly để lấy access_token mới
router.post("/refresh", async (req, res) => {
  const refreshToken = req.cookies?.[REFRESH_COOKIE];
  if (!refreshToken) return res.status(401).json({ error: "unauthorized", message: "Không có refresh token" });

  try {
    const tokenRes = await fetch(`${config.KC_BASE}/token`, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        grant_type: "refresh_token",
        client_id: config.CLIENT_ID,
        client_secret: config.CLIENT_SECRET,
        refresh_token: refreshToken,
      }),
    });

    const tokens = await tokenRes.json();
    if (!tokenRes.ok) {
      res.clearCookie(ACCESS_COOKIE, { path: "/" });
      res.clearCookie(REFRESH_COOKIE, { path: "/" });
      return res.status(401).json({ error: tokens.error, message: tokens.error_description || "Refresh failed" });
    }

    setSessionCookies(res, tokens);
    res.json({ authenticated: true });
  } catch (err) {
    res.status(502).json({ error: "keycloak_unreachable", message: err.message });
  }
});

// GET /api/auth/me — FE gọi để biết đã đăng nhập chưa + lấy thông tin user (không lộ token)
router.get("/me", async (req, res) => {
  const token = req.cookies?.[ACCESS_COOKIE];
  if (!token) return res.status(401).json({ error: "unauthorized" });

  try {
    const userRes = await fetch(`${config.KC_BASE}/userinfo`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!userRes.ok) return res.status(401).json({ error: "unauthorized" });

    const userinfo = await userRes.json();
    res.json({ authenticated: true, userinfo });
  } catch (err) {
    res.status(502).json({ error: "keycloak_unreachable", message: err.message });
  }
});

// POST /api/auth/logout — xóa cookie phía server; FE tự redirect sang Keycloak RP-Initiated Logout
router.post("/logout", (req, res) => {
  res.clearCookie(ACCESS_COOKIE, { path: "/" });
  res.clearCookie(REFRESH_COOKIE, { path: "/" });
  res.json({ ok: true });
});

module.exports = router;

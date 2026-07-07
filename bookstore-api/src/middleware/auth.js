const config = require("../config");

// Lấy token từ header "Authorization: Bearer ..." hoặc cookie access_token,
// rồi hỏi Keycloak qua introspection endpoint xem token còn hiệu lực không.
// Introspection là server-to-server nên dùng được client_secret an toàn.
async function requireToken(req, res, next) {
  let token = null;

  const auth = req.headers.authorization;
  if (auth?.startsWith("Bearer ")) {
    token = auth.slice(7);
  } else if (req.headers.cookie) {
    const match = req.headers.cookie.match(/(?:^|;\s*)access_token=([^;]+)/);
    if (match) token = decodeURIComponent(match[1]);
  }

  if (!token) {
    return res.status(401).json({
      error: "unauthorized",
      message: "Missing access token (Bearer header hoặc cookie access_token)",
    });
  }

  try {
    // POST /token/introspect — Keycloak trả { active: true/false, ...claims }
    const intro = await fetch(`${config.KC_BASE}/token/introspect`, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        client_id: config.CLIENT_ID,
        client_secret: config.CLIENT_SECRET,
        token,
      }),
    });
    const body = await intro.json();
    console.log("Token introspection result:", body);
    if (!intro.ok || !body.active) {
      return res.status(401).json({ error: "unauthorized", message: "Token invalid or expired" });
    }

    req.tokenInfo = body; // { sub, preferred_username, email, exp, ... }
    next();
  } catch (err) {
    res.status(502).json({ error: "keycloak_unreachable", message: err.message });
  }
}

module.exports = { requireToken };

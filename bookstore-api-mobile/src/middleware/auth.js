const jwt = require("jsonwebtoken");
const jwksClient = require("jwks-rsa");
const config = require("../config");

// Verify chữ ký JWT cục bộ bằng public key lấy từ Keycloak JWKS — không cần
// client_secret (phù hợp public client như app mobile), không round-trip mỗi request
// (jwks-rsa cache key theo kid).
const client = jwksClient({ jwksUri: config.JWKS_URI, cache: true, cacheMaxAge: 10 * 60 * 1000 });

function getKey(header, callback) {
  client.getSigningKey(header.kid, (err, key) => {
    if (err) return callback(err);
    callback(null, key.getPublicKey());
  });
}

async function requireToken(req, res, next) {
  const auth = req.headers.authorization;
  if (!auth?.startsWith("Bearer ")) {
    return res.status(401).json({ error: "unauthorized", message: "Missing Authorization: Bearer <token>" });
  }
  const token = auth.slice(7);

  jwt.verify(
    token,
    getKey,
    { algorithms: ["RS256"], issuer: config.ISSUER },
    (err, decoded) => {
      if (err) {
        return res.status(401).json({ error: "unauthorized", message: err.message });
      }
      req.tokenInfo = decoded; // { sub, preferred_username, email, exp, ... }
      next();
    }
  );
}

module.exports = { requireToken };

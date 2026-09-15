const jwt = require("jsonwebtoken");
const jwksClient = require("jwks-rsa");
const config = require("../config");

// Verify access_token app Android gửi lên khi tự động approve phiên QR — cùng cách
// bookstore-api-mobile verify (JWKS + issuer), không phân biệt client vì cùng realm.
const client = jwksClient({ jwksUri: config.JWKS_URI, cache: true, cacheMaxAge: 10 * 60 * 1000 });

function getKey(header, callback) {
  client.getSigningKey(header.kid, (err, key) => {
    if (err) return callback(err);
    callback(null, key.getPublicKey());
  });
}

function requireToken(req, res, next) {
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
      req.tokenInfo = decoded;
      next();
    }
  );
}

module.exports = { requireToken };

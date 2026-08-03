// Cấu hình đọc từ .env (xem .env.example) — không hardcode giá trị trong code.
require("dotenv").config({ path: require("path").join(__dirname, "..", ".env"), quiet: true });

function required(name) {
  const value = process.env[name];
  if (!value) {
    throw new Error(`Missing env var ${name} — copy .env.example thành .env rồi điền giá trị`);
  }
  return value;
}

module.exports = {
  PORT: required("PORT"),

  // Domain public mà app Android thực sự thấy (vd ngrok https://...) — phải khớp
  // claim "iss" trong access token, và cũng dùng gọi JWKS luôn cho đơn giản.
  KEYCLOAK_URL: required("KEYCLOAK_URL"),
  REALM: required("REALM"),
  CLIENT_ID: required("CLIENT_ID"),

  get ISSUER() {
    return `${this.KEYCLOAK_URL}/realms/${this.REALM}`;
  },
  get JWKS_URI() {
    return `${this.ISSUER}/protocol/openid-connect/certs`;
  },
};

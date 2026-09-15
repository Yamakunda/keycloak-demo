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

  // Domain public mà điện thoại thực sự thấy khi quét QR (vd ngrok https://...).
  KEYCLOAK_URL: required("KEYCLOAK_URL"),
  REALM: required("REALM"),
  CLIENT_ID: required("CLIENT_ID"),
  CLIENT_SECRET: required("CLIENT_SECRET"),

  get ISSUER() {
    return `${this.KEYCLOAK_URL}/realms/${this.REALM}`;
  },
  get DEVICE_AUTH_ENDPOINT() {
    return `${this.ISSUER}/protocol/openid-connect/auth/device`;
  },
  get TOKEN_ENDPOINT() {
    return `${this.ISSUER}/protocol/openid-connect/token`;
  },
};

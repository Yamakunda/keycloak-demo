// Cấu hình đọc từ .env (xem .env.example) — không hardcode giá trị trong code.
// Trùng realm/client với frontend (demo-keycloak/src/config/keycloak.ts).
require("dotenv").config({ path: require("path").join(__dirname, "..", ".env") });

function required(name) {
  const value = process.env[name];
  if (!value) {
    throw new Error(`Missing env var ${name} — copy .env.example thành .env rồi điền giá trị`);
  }
  return value;
}

module.exports = {
  PORT: required("PORT"),
  FRONTEND_ORIGIN: required("FRONTEND_ORIGIN"),
  REDIRECT_URI: required("REDIRECT_URI"),

  KEYCLOAK_URL: required("KEYCLOAK_URL"),
  REALM: required("REALM"),
  CLIENT_ID: required("CLIENT_ID"),
  CLIENT_SECRET: required("CLIENT_SECRET"),

  get KC_BASE() {
    return `${this.KEYCLOAK_URL}/realms/${this.REALM}/protocol/openid-connect`;
  },
};

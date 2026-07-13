// Cấu hình đọc từ .env (xem .env.example) — không hardcode giá trị trong code.
// SP_ENTITY_ID phải trùng Client ID của client SAML trong Keycloak (bookstore-saml).
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
  BASE_URL: required("BASE_URL"),
  FRONTEND_URL: required("FRONTEND_URL"),

  KEYCLOAK_URL: required("KEYCLOAK_URL"),
  REALM: required("REALM"),
  SP_ENTITY_ID: required("SP_ENTITY_ID"),
  SESSION_SECRET: required("SESSION_SECRET"),

  // Endpoint SAML duy nhất của Keycloak: nhận cả AuthnRequest lẫn LogoutRequest
  get IDP_SSO_URL() {
    return `${this.KEYCLOAK_URL}/realms/${this.REALM}/protocol/saml`;
  },
  get IDP_METADATA_URL() {
    return `${this.IDP_SSO_URL}/descriptor`;
  },
};

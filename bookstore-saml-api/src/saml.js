const { Strategy: SamlStrategy } = require("@node-saml/passport-saml");
const config = require("./config");

// Lấy certificate ký của realm từ IdP metadata để khỏi phải copy-paste vào config.
// Retry vì Keycloak có thể chưa boot xong khi container này khởi động.
async function fetchIdpCert() {
  for (let attempt = 1; attempt <= 30; attempt++) {
    try {
      const res = await fetch(config.IDP_METADATA_URL);
      if (res.ok) {
        const xml = await res.text();
        const m = xml.match(/<ds:X509Certificate>([^<]+)<\/ds:X509Certificate>/);
        if (m) return m[1].replace(/\s+/g, "");
        throw new Error("no X509Certificate found in IdP metadata");
      }
      throw new Error(`HTTP ${res.status}`);
    } catch (err) {
      console.log(`[saml] waiting for Keycloak metadata (${attempt}/30): ${err.message}`);
      await new Promise((r) => setTimeout(r, 2000));
    }
  }
  throw new Error(`could not fetch IdP metadata from ${config.IDP_METADATA_URL}`);
}

function createSamlStrategy(idpCert) {
  return new SamlStrategy(
    {
      issuer: config.SP_ENTITY_ID,
      callbackUrl: `${config.BASE_URL}/saml/acs`,
      entryPoint: config.IDP_SSO_URL,
      logoutUrl: config.IDP_SSO_URL,
      logoutCallbackUrl: `${config.BASE_URL}/saml/logout/callback`,
      idpCert,
      identifierFormat: "urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified",
      wantAssertionsSigned: true,
      wantAuthnResponseSigned: true,
      // Demo: không lưu InResponseTo cache để restart app không làm hỏng flow đang dở
      validateInResponseTo: "never",
      acceptedClockSkewMs: 5000,
    },
    // signon verify: profile chính là các attribute từ SAML assertion
    (profile, done) => done(null, profile),
    // logout verify (IdP-initiated logout)
    (profile, done) => done(null, profile)
  );
}

module.exports = { fetchIdpCert, createSamlStrategy };

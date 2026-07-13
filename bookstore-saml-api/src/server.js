const express = require("express");
const session = require("express-session");
const cors = require("cors");
const passport = require("passport");
const config = require("./config");
const { fetchIdpCert, createSamlStrategy } = require("./saml");
const { ensureAuth } = require("./middleware/auth");
const buildAuthRouter = require("./routes/auth");
const booksRouter = require("./routes/books");

// Bootstrap async vì phải fetch IdP cert từ Keycloak trước khi tạo strategy
async function main() {
  const idpCert = await fetchIdpCert();
  console.log("[saml] fetched IdP signing certificate from realm metadata");

  const samlStrategy = createSamlStrategy(idpCert);
  passport.use("saml", samlStrategy);
  passport.serializeUser((user, done) => done(null, user));
  passport.deserializeUser((user, done) => done(null, user));

  const app = express();
  app.use(express.urlencoded({ extended: false }));
  app.use(express.json());
  // FE (3032) và API (3033) khác origin nhưng cùng site (localhost) →
  // cookie sameSite=lax vẫn được browser gửi kèm fetch, chỉ cần CORS credentials.
  app.use(cors({ origin: config.FRONTEND_URL, credentials: true }));
  app.use(
    session({
      secret: config.SESSION_SECRET,
      resave: false,
      saveUninitialized: false,
      cookie: { httpOnly: true, sameSite: "lax" },
    })
  );
  app.use(passport.initialize());
  app.use(passport.session());

  // Health check — không cần session
  app.get("/health", (req, res) => res.json({ status: "ok" }));

  // SAML login/logout/metadata + /api/me
  app.use("/", buildAuthRouter(samlStrategy));

  // Toàn bộ API sách yêu cầu session đã đăng nhập (POST/DELETE cần thêm book-admin)
  app.use("/api/books", ensureAuth, booksRouter);

  app.use((req, res) => res.status(404).json({ error: "not_found" }));

  app.listen(config.PORT, () => {
    console.log(`[bookstore-saml-api] Running at ${config.BASE_URL}`);
    console.log(`  SP entityID : ${config.SP_ENTITY_ID}`);
    console.log(`  SP metadata : ${config.BASE_URL}/metadata`);
    console.log(`  IdP SSO URL : ${config.IDP_SSO_URL}`);
    console.log(`  Frontend    : ${config.FRONTEND_URL}`);
  });
}

main().catch((err) => {
  console.error("fatal:", err);
  process.exit(1);
});

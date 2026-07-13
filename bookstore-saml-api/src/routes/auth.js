const express = require("express");
const passport = require("passport");
const config = require("../config");
const { rolesOf, ensureAuth } = require("../middleware/auth");

// Factory vì router cần strategy đã khởi tạo xong (phải fetch IdP cert trước) —
// khác routes/auth.js của các API OAuth vốn chỉ cần config tĩnh.
module.exports = function buildAuthRouter(samlStrategy) {
  const router = express.Router();

  // --- SAML endpoints (browser navigation nguyên trang, không phải fetch) ---

  // Bắt đầu SP-initiated SSO: build AuthnRequest rồi redirect sang Keycloak
  router.get("/login", passport.authenticate("saml"));

  // Assertion Consumer Service: Keycloak POST SAMLResponse (đã ký) vào đây
  router.post(
    "/saml/acs",
    passport.authenticate("saml", { failureRedirect: "/login-failed" }),
    (req, res) => res.redirect(config.FRONTEND_URL)
  );

  router.get("/login-failed", (req, res) =>
    res.redirect(`${config.FRONTEND_URL}?error=login_failed`)
  );

  // SP metadata: dán URL này (hoặc import file) khi cấu hình client trong Keycloak
  router.get("/metadata", (req, res) => {
    res.type("application/xml").send(samlStrategy.generateServiceProviderMetadata(null, null));
  });

  // SP-initiated Single Logout: gửi LogoutRequest sang Keycloak
  router.get("/logout", (req, res, next) => {
    if (!req.isAuthenticated()) return res.redirect(config.FRONTEND_URL);
    samlStrategy.logout(req, (err, url) => {
      if (err) return next(err);
      req.logout(() => res.redirect(url));
    });
  });

  // Keycloak redirect về đây kèm LogoutResponse (demo: chỉ hủy session)
  router.all("/saml/logout/callback", (req, res) => {
    req.session.destroy(() => res.redirect(config.FRONTEND_URL));
  });

  // --- Session info cho FE ---------------------------------------------------

  // GET /api/me — user hiện tại + roles + attribute thô từ assertion
  router.get("/api/me", ensureAuth, (req, res) => {
    const attributes = Object.fromEntries(
      Object.entries(req.user).filter(
        ([k, v]) => typeof v !== "function" && !k.startsWith("saml")
      )
    );
    res.json({
      username: req.user.nameID,
      roles: rolesOf(req.user),
      isAdmin: rolesOf(req.user).includes("book-admin"),
      attributes,
    });
  });

  return router;
};

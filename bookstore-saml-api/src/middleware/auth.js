// Khác các API OAuth (introspection từng request): ở đây user đã được xác thực
// lúc Keycloak POST assertion vào ACS; các request sau chỉ cần session cookie
// (passport.session() gắn lại req.user từ session).

// Role nằm trong assertion dưới dạng attribute "Role" (saml-role-list-mapper,
// single=true → một attribute nhiều value; vẫn phòng trường hợp value đơn lẻ).
function rolesOf(user) {
  const r = user?.["Role"] ?? [];
  return Array.isArray(r) ? r : [r];
}

function ensureAuth(req, res, next) {
  if (req.isAuthenticated()) return next();
  res.status(401).json({ error: "not_authenticated" });
}

function ensureRole(role) {
  return (req, res, next) => {
    if (!req.isAuthenticated()) return res.status(401).json({ error: "not_authenticated" });
    if (rolesOf(req.user).includes(role)) return next();
    res.status(403).json({ error: "forbidden", requiredRole: role });
  };
}

module.exports = { rolesOf, ensureAuth, ensureRole };

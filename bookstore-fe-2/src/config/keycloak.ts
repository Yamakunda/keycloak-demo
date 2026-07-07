// Cấu hình Keycloak — confidential client (Client authentication = On).
// client_secret và redirect_uri của Authorization Code flow chỉ tồn tại ở backend
// (bookstore-api-2/.env, .../src/routes/auth.js) — FE không tham gia bước redirect
// sang Keycloak /auth hay đổi code lấy token (xem authService.ts).
// CLIENT_ID ở đây chỉ dùng để build URL RP-Initiated Logout.

import { required } from "./env";

export const KEYCLOAK_URL = required("REACT_APP_KEYCLOAK_URL");
export const REALM = required("REACT_APP_REALM");
export const CLIENT_ID = required("REACT_APP_CLIENT_ID");
export const POST_LOGOUT_REDIRECT_URI = window.location.origin + "/login";

export const KC_BASE = `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect`;

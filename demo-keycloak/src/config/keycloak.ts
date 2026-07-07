// Cấu hình Keycloak — confidential client (Client authentication = On).
// Giá trị đọc từ .env (prefix REACT_APP_, nhúng vào bundle lúc build — xem .env.example).
// LƯU Ý: secret nằm trong bundle JS nên chỉ dùng kiểu này cho demo;
// production thì token exchange + secret phải nằm ở backend.

import { required } from "./env";

export const KEYCLOAK_URL = required("REACT_APP_KEYCLOAK_URL");
export const REALM = required("REACT_APP_REALM");
export const CLIENT_ID = required("REACT_APP_CLIENT_ID");
export const CLIENT_SECRET = required("REACT_APP_CLIENT_SECRET");
export const REDIRECT_URI = window.location.origin + "/callback";
export const POST_LOGOUT_REDIRECT_URI = window.location.origin + "/login";

export const KC_BASE = `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect`;

import { required } from "./env";

// OAuth client public thật (Authorization Code + PKCE) trỏ thẳng vào Keycloak — dùng để
// test Authenticator SPI "Đăng nhập bằng QR" xuất hiện trên chính trang login Keycloak
// qua cơ chế "Try another way". Xem ../../keycloak-spi-qr-login
export const KEYCLOAK_URL = required("REACT_APP_KEYCLOAK_URL");
export const REALM = required("REACT_APP_REALM");
export const CLIENT_ID = required("REACT_APP_CLIENT_ID");
export const REDIRECT_URI = required("REACT_APP_REDIRECT_URI");

export const REALM_URL = `${KEYCLOAK_URL}/realms/${REALM}`;

// Backend bookstore-api-mobile (Express) — xem ../../bookstore-api-mobile
export const BOOKS_API_URL = required("REACT_APP_BOOKS_API_URL");

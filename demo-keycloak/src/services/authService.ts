// Auth service — mọi tương tác với Keycloak API nằm ở đây.
// Không có state React; state do AuthContext quản lý.

import { KC_BASE, CLIENT_ID, CLIENT_SECRET, REDIRECT_URI, POST_LOGOUT_REDIRECT_URI } from "../config/keycloak";
import { setCookie, getCookie, deleteCookie } from "../utils/cookie";
import { randomString } from "../utils/random";

export interface TokenResponse {
  access_token: string;
  id_token: string;
  refresh_token: string;
  expires_in: number;
  refresh_expires_in: number;
  token_type: string;
  scope: string;
  session_state?: string;
}

export interface UserInfo {
  sub: string;
  name?: string;
  preferred_username?: string;
  given_name?: string;
  family_name?: string;
  email?: string;
  [claim: string]: unknown;
}

interface KeycloakErrorBody {
  error?: string;
  error_description?: string;
}

export function getAccessToken(): string | null {
  return getCookie("access_token");
}

// Step 1 — Redirect sang Keycloak /auth (Authorization Code flow)
export function login(): void {
  const state = randomString(32); // chống CSRF trên callback
  sessionStorage.setItem("oauth_state", state);

  const params = new URLSearchParams({
    response_type: "code",
    client_id: CLIENT_ID,
    redirect_uri: REDIRECT_URI,
    scope: "openid profile email",
    state,
  });

  window.location.href = `${KC_BASE}/auth?${params}`;
}

// Step 2+3 — Nhận ?code= từ Keycloak và đổi lấy token (POST /token)
export async function exchangeCode(code: string | null, returnedState: string | null): Promise<TokenResponse> {
  if (!code) {
    throw new Error("Thiếu authorization code trên callback");
  }
  if (returnedState !== sessionStorage.getItem("oauth_state")) {
    throw new Error("state không khớp — có thể là CSRF, hủy đăng nhập");
  }

  const res = await fetch(`${KC_BASE}/token`, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "authorization_code",
      client_id: CLIENT_ID,
      client_secret: CLIENT_SECRET,
      redirect_uri: REDIRECT_URI,
      code,
    }),
  });

  const body: TokenResponse & KeycloakErrorBody = await res.json();
  if (!res.ok) throw new Error(body.error_description || body.error || "Token exchange failed");

  // Lưu access token vào cookie để dùng lại (DevTools → Application → Cookies)
  setCookie("access_token", body.access_token, body.expires_in);
  sessionStorage.setItem("id_token", body.id_token);
  sessionStorage.setItem("refresh_token", body.refresh_token);
  sessionStorage.removeItem("oauth_state");

  return body;
}

// Đổi refresh_token lấy access_token mới, cập nhật lại cookie
export async function refreshTokens(): Promise<TokenResponse> {
  const refreshToken = sessionStorage.getItem("refresh_token");
  if (!refreshToken) throw new Error("Không có refresh token — hãy đăng nhập lại");

  const res = await fetch(`${KC_BASE}/token`, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "refresh_token",
      client_id: CLIENT_ID,
      client_secret: CLIENT_SECRET,
      refresh_token: refreshToken,
    }),
  });

  const body: TokenResponse & KeycloakErrorBody = await res.json();
  if (!res.ok) throw new Error(body.error_description || "Refresh failed");

  setCookie("access_token", body.access_token, body.expires_in);
  if (body.refresh_token) sessionStorage.setItem("refresh_token", body.refresh_token);

  return body;
}

// Step 4 — Dùng access token trong cookie để gọi API /userinfo
export async function fetchUserinfo(): Promise<UserInfo> {
  const token = getAccessToken();
  if (!token) {
    throw new Error("Cookie access_token đã hết hạn — hãy đăng nhập lại hoặc refresh.");
  }

  const res = await fetch(`${KC_BASE}/userinfo`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) {
    throw new Error(`GET /userinfo thất bại (${res.status}) — token không hợp lệ hoặc hết hạn`);
  }
  return res.json() as Promise<UserInfo>;
}

// RP-Initiated Logout — xóa cookie + kết thúc SSO session tại Keycloak
export function logout(): void {
  const idToken = sessionStorage.getItem("id_token");
  deleteCookie("access_token");
  sessionStorage.clear();

  const params = new URLSearchParams({
    post_logout_redirect_uri: POST_LOGOUT_REDIRECT_URI,
    ...(idToken ? { id_token_hint: idToken } : { client_id: CLIENT_ID }),
  });
  window.location.href = `${KC_BASE}/logout?${params}`;
}

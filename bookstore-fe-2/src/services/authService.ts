// Auth service — mọi tương tác với Keycloak/bookstore-api liên quan tới đăng nhập nằm ở đây.
// Toàn bộ Authorization Code flow (redirect sang Keycloak, nhận code, đổi lấy token)
// diễn ra ở BACKEND (bookstore-api-2/src/routes/auth.js) — FE chỉ điều hướng trình duyệt
// tới các endpoint /api/auth/* của backend, không bao giờ thấy client_secret hay
// access_token/refresh_token thô (backend set cookie httpOnly).

import { KC_BASE, CLIENT_ID, POST_LOGOUT_REDIRECT_URI } from "../config/keycloak";
import { API_URL } from "../config/api";

export interface UserInfo {
  sub: string;
  name?: string;
  preferred_username?: string;
  given_name?: string;
  family_name?: string;
  email?: string;
  [claim: string]: unknown;
}

interface ApiErrorBody {
  error?: string;
  message?: string;
}

// Bước 1-3 gộp ở backend — FE chỉ redirect toàn trang sang GET /api/auth/login.
// Backend tự sinh state, redirect sang Keycloak /auth, nhận code ở /api/auth/callback,
// đổi code lấy token, set cookie httpOnly, rồi redirect về trang chủ SPA.
export function login(): void {
  window.location.href = `${API_URL}/api/auth/login`;
}

// Đổi refresh_token (cookie httpOnly, backend tự đọc) lấy access_token mới
export async function refreshTokens(): Promise<void> {
  const res = await fetch(`${API_URL}/api/auth/refresh`, {
    method: "POST",
    credentials: "include",
  });

  const body: ApiErrorBody = await res.json();
  if (!res.ok) throw new Error(body.message || "Refresh failed");
}

// Kiểm tra phiên đăng nhập hiện tại + lấy thông tin user, dựa vào cookie httpOnly
// mà browser tự động gửi kèm (không đọc được token từ JS).
export async function fetchMe(): Promise<UserInfo | null> {
  const res = await fetch(`${API_URL}/api/auth/me`, {
    credentials: "include",
  });
  if (!res.ok) return null;

  const body = await res.json();
  return body.userinfo as UserInfo;
}

// RP-Initiated Logout — xóa cookie phía backend rồi kết thúc SSO session tại Keycloak
export async function logout(): Promise<void> {
  await fetch(`${API_URL}/api/auth/logout`, {
    method: "POST",
    credentials: "include",
  }).catch(() => {});

  const params = new URLSearchParams({
    post_logout_redirect_uri: POST_LOGOUT_REDIRECT_URI,
    client_id: CLIENT_ID,
  });
  window.location.href = `${KC_BASE}/logout?${params}`;
}

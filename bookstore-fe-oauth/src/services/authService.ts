// Auth service — mọi tương tác với Keycloak/bookstore-api liên quan tới đăng nhập nằm ở đây.
// Toàn bộ Authorization Code flow (redirect sang Keycloak, nhận code, đổi lấy token)
// diễn ra ở BACKEND (bookstore-api-2/src/routes/auth.js) — FE chỉ điều hướng trình duyệt
// tới các endpoint /api/auth/* của backend, không bao giờ thấy client_secret hay
// access_token/refresh_token thô (backend set cookie httpOnly).

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

// RP-Initiated Logout — backend xóa cookie rồi trả về logoutUrl (kèm id_token_hint,
// nên Keycloak logout ngay không hỏi xác nhận); FE chỉ việc redirect tới đó.
export async function logout(): Promise<void> {
  let logoutUrl: string | undefined;
  try {
    const res = await fetch(`${API_URL}/api/auth/logout`, {
      method: "POST",
      credentials: "include",
    });
    const body = await res.json();
    logoutUrl = body.logoutUrl;
  } catch {
    // backend không phản hồi — vẫn đưa user về trang login
  }
  window.location.href = logoutUrl || "/login";
}

import { CLIENT_ID, REALM_URL, REDIRECT_URI } from "../config/api";
import { deriveCodeChallenge, generateCodeVerifier, generateState } from "./pkce";

const VERIFIER_KEY = "qr_test_pkce_verifier";
const STATE_KEY = "qr_test_oauth_state";

export interface TokenResponse {
  access_token: string;
  refresh_token?: string;
  expires_in: number;
  token_type: string;
  scope: string;
  id_token?: string;
}

// Redirect thẳng vào trang login mặc định của Keycloak — nơi có nút "Try another way" để
// chọn "Đăng nhập bằng QR" (Authenticator SPI). Không tự vẽ QR trong React như bản cũ.
export async function startLogin(): Promise<void> {
  const verifier = generateCodeVerifier();
  const challenge = await deriveCodeChallenge(verifier);
  const state = generateState();
  sessionStorage.setItem(VERIFIER_KEY, verifier);
  sessionStorage.setItem(STATE_KEY, state);

  const params = new URLSearchParams({
    client_id: CLIENT_ID,
    redirect_uri: REDIRECT_URI,
    response_type: "code",
    scope: "openid profile email",
    code_challenge: challenge,
    code_challenge_method: "S256",
    state,
  });

  window.location.href = `${REALM_URL}/protocol/openid-connect/auth?${params.toString()}`;
}

export async function handleCallback(code: string, state: string): Promise<TokenResponse> {
  const expectedState = sessionStorage.getItem(STATE_KEY);
  const verifier = sessionStorage.getItem(VERIFIER_KEY);
  sessionStorage.removeItem(STATE_KEY);
  sessionStorage.removeItem(VERIFIER_KEY);

  if (!verifier || !expectedState || state !== expectedState) {
    throw new Error("State không khớp hoặc thiếu code_verifier — thử đăng nhập lại.");
  }

  const body = new URLSearchParams({
    grant_type: "authorization_code",
    client_id: CLIENT_ID,
    redirect_uri: REDIRECT_URI,
    code,
    code_verifier: verifier,
  });

  const res = await fetch(`${REALM_URL}/protocol/openid-connect/token`, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: body.toString(),
  });

  if (!res.ok) {
    const text = await res.text().catch(() => "");
    throw new Error(`Đổi code lấy token thất bại: HTTP ${res.status} ${text}`);
  }

  return res.json();
}

// RP-Initiated Logout (OIDC chuẩn) — kết thúc session thật trên Keycloak, không chỉ xoá
// token khỏi state của trang. Cần id_token_hint để Keycloak không hiện màn hình xác nhận.
export function logout(idToken?: string): void {
  const params = new URLSearchParams({
    client_id: CLIENT_ID,
    post_logout_redirect_uri: `${window.location.origin}/`,
  });
  if (idToken) params.set("id_token_hint", idToken);

  window.location.href = `${REALM_URL}/protocol/openid-connect/logout?${params.toString()}`;
}

export function decodePreferredUsername(idToken: string): string | undefined {
  try {
    const payload = idToken.split(".")[1];
    const json = atob(payload.replace(/-/g, "+").replace(/_/g, "/"));
    const data = JSON.parse(json);
    return data.preferred_username;
  } catch {
    return undefined;
  }
}

// PKCE (RFC 7636) cho public client — không có client_secret nên bắt buộc dùng để chống
// authorization code interception.

function base64UrlEncode(bytes: Uint8Array): string {
  let str = "";
  bytes.forEach((b) => (str += String.fromCharCode(b)));
  return btoa(str).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

export function generateCodeVerifier(): string {
  const bytes = new Uint8Array(32);
  crypto.getRandomValues(bytes);
  return base64UrlEncode(bytes);
}

export async function deriveCodeChallenge(verifier: string): Promise<string> {
  // crypto.subtle chỉ tồn tại trong secure context (https:// hoặc localhost) — truy cập
  // bằng http://<IP LAN> (vd để test từ điện thoại) sẽ có crypto.subtle === undefined và
  // throw TypeError mập mờ nếu không check trước.
  if (!crypto.subtle) {
    throw new Error(
      "Trình duyệt chặn Web Crypto API vì trang đang mở qua http://IP (không phải https:// hoặc localhost). " +
        "Hãy mở bằng http://localhost:3060 nếu test trên chính máy này, hoặc bật cờ " +
        "chrome://flags/#unsafely-treat-insecure-origin-as-secure và thêm origin này."
    );
  }
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(verifier));
  return base64UrlEncode(new Uint8Array(digest));
}

export function generateState(): string {
  return generateCodeVerifier();
}

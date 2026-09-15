import { API_URL } from "../config/api";

export interface QrStartResponse {
  session_id: string;
  expires_in: number;
}

export interface TokenResponse {
  access_token: string;
  refresh_token?: string;
  expires_in: number;
  token_type: string;
  scope: string;
}

export type QrPollResult =
  | { status: "pending" }
  | { status: "expired" }
  | { status: "approved"; token: TokenResponse; username?: string };

export async function startQrSession(): Promise<QrStartResponse> {
  const res = await fetch(`${API_URL}/qr/start`, { method: "POST" });
  if (!res.ok) {
    throw new Error("Không khởi tạo được phiên đăng nhập QR");
  }
  return res.json();
}

export async function pollQrSession(sessionId: string): Promise<QrPollResult> {
  const res = await fetch(`${API_URL}/qr/poll`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ session_id: sessionId }),
  });

  if (res.status === 404) return { status: "expired" };

  const data = await res.json();
  if (data.status === "approved") {
    return { status: "approved", token: data.token, username: data.username };
  }
  return { status: "pending" };
}

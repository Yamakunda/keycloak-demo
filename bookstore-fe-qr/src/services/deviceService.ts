import { API_URL } from "../config/api";

export interface DeviceStartResponse {
  device_code: string;
  user_code: string;
  verification_uri: string;
  verification_uri_complete: string;
  expires_in: number;
  interval: number;
}

export interface TokenResponse {
  access_token: string;
  refresh_token?: string;
  expires_in: number;
  token_type: string;
  scope: string;
}

interface PendingResponse {
  error: "authorization_pending" | "slow_down" | "expired_token" | "access_denied";
  error_description?: string;
}

export type PollResult =
  | { status: "pending" | "slow_down" }
  | { status: "denied" | "expired" }
  | { status: "approved"; token: TokenResponse };

export async function startDeviceLogin(): Promise<DeviceStartResponse> {
  const res = await fetch(`${API_URL}/device/start`, { method: "POST" });
  if (!res.ok) {
    throw new Error("Không khởi tạo được phiên đăng nhập QR");
  }
  return res.json();
}

export async function pollDeviceLogin(deviceCode: string): Promise<PollResult> {
  const res = await fetch(`${API_URL}/device/poll`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ device_code: deviceCode }),
  });

  const data = await res.json();

  if (res.ok) {
    return { status: "approved", token: data as TokenResponse };
  }

  const pending = data as PendingResponse;
  if (pending.error === "authorization_pending") return { status: "pending" };
  if (pending.error === "slow_down") return { status: "slow_down" };
  if (pending.error === "expired_token") return { status: "expired" };
  return { status: "denied" };
}

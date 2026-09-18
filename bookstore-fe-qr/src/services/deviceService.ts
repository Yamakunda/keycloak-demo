import { API_URL } from "../config/api";

// OAuth 2.0 Device Authorization Grant (RFC 8628) qua backend trung gian bookstore-api-qr —
// backend giữ client_secret, FE không bao giờ thấy nó. QR vẽ chính verification_uri_complete
// Keycloak trả về; điện thoại quét QR chỉ mở URL đó (Custom Tabs) và bấm "Cho phép" trên
// trang xác nhận thật của Keycloak — không có access_token nào được gửi qua lại giữa app và
// backend này.
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

export type DevicePollResult =
  | { status: "pending" }
  | { status: "slow_down" }
  | { status: "expired" }
  | { status: "denied" }
  | { status: "approved"; token: TokenResponse };

export async function startDeviceFlow(): Promise<DeviceStartResponse> {
  const res = await fetch(`${API_URL}/device/start`, { method: "POST" });
  if (!res.ok) {
    throw new Error("Không khởi tạo được phiên đăng nhập QR");
  }
  return res.json();
}

export async function pollDeviceToken(deviceCode: string): Promise<DevicePollResult> {
  const res = await fetch(`${API_URL}/device/poll`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ device_code: deviceCode }),
  });

  if (res.ok) {
    return { status: "approved", token: await res.json() };
  }

  // Keycloak trả 400 kèm error=authorization_pending/slow_down trong lúc chờ — trạng thái
  // bình thường của polling, không phải lỗi thật.
  const data = await res.json().catch(() => ({}));
  switch (data.error) {
    case "authorization_pending":
      return { status: "pending" };
    case "slow_down":
      return { status: "slow_down" };
    case "access_denied":
      return { status: "denied" };
    case "expired_token":
      return { status: "expired" };
    default:
      return { status: "expired" };
  }
}

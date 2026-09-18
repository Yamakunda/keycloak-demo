/**
 * KEYCLOAK_SPI: QR vẽ bởi qr-login.ftl (trang login mặc định của Keycloak, qua "Try another
 * way") → chạy thẳng trong Keycloak (keycloak-spi-qr-login), JSON {"apiUrl":..., "sessionId":...}
 * — 2 bước: "/qr-login/scan" ngay khi quét (chỉ ghi nhận, CHƯA cấp quyền), rồi
 * "/qr-login/approve" sau khi người dùng xác nhận bằng biometric trên chính điện thoại.
 * Có thêm "/qr-login/cancel" nếu từ chối xác nhận. Tương đương QrLoginApi.kt.
 */

interface QrApproveRequestBody {
  session_id: string;
  access_token: string;
}

type ApiResult = { ok: true } | { ok: false; error: string };

async function postSessionAction(url: string, body: QrApproveRequestBody): Promise<ApiResult> {
  try {
    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${body.access_token}`,
      },
      body: JSON.stringify(body),
    });

    if (!response.ok) {
      const errorBody = await response.text().catch(() => '');
      return { ok: false, error: `HTTP ${response.status}: ${errorBody}` };
    }
    return { ok: true };
  } catch (e) {
    return { ok: false, error: e instanceof Error ? e.message : 'Lỗi không xác định' };
  }
}

export async function scan(apiUrl: string, sessionId: string, accessToken: string): Promise<ApiResult> {
  return postSessionAction(`${apiUrl}/qr-login/scan`, { session_id: sessionId, access_token: accessToken });
}

export async function approve(apiUrl: string, sessionId: string, accessToken: string): Promise<ApiResult> {
  return postSessionAction(`${apiUrl}/qr-login/approve`, { session_id: sessionId, access_token: accessToken });
}

// Người dùng bấm "Từ chối" hoặc biometric thất bại — trả phiên QR về trạng thái chờ quét
// lại thay vì để thiết bị kia bị treo mãi ở "đã quét, chờ xác nhận".
export async function cancel(apiUrl: string, sessionId: string, accessToken: string): Promise<ApiResult> {
  return postSessionAction(`${apiUrl}/qr-login/cancel`, { session_id: sessionId, access_token: accessToken });
}

export function parseQrPayload(rawValue: string): { apiUrl: string; sessionId: string } | null {
  try {
    const obj = JSON.parse(rawValue);
    if (typeof obj?.apiUrl !== 'string' || typeof obj?.sessionId !== 'string') return null;
    return { apiUrl: obj.apiUrl, sessionId: obj.sessionId };
  } catch {
    return null;
  }
}

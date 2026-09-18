package com.snp.bookstorebio.data.source.remote

import com.snp.bookstorebio.data.source.remote.dto.QrApproveRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Có 2 nguồn QR song song, khác hẳn cơ chế:
 *  - LEGACY: QR vẽ bởi bookstore-fe-qr, encode thẳng "verification_uri_complete" của OAuth
 *    2.0 Device Authorization Grant (RFC 8628) — app KHÔNG gọi API nào ở đây, chỉ mở URL đó
 *    bằng Custom Tabs (xem MainActivity), người dùng tự bấm "Cho phép" trên trang xác nhận
 *    thật của Keycloak. Xem AppViewModel.onQrCodeScanned.
 *  - KEYCLOAK_SPI: QR vẽ bởi qr-login.ftl (ngay trên trang login mặc định của Keycloak,
 *    qua "Try another way") → chạy thẳng trong Keycloak (keycloak-spi-qr-login), JSON
 *    {"apiUrl":..., "sessionId":...} — 2 bước: "/qr-login/scan" ngay khi quét (chỉ ghi nhận,
 *    CHƯA cấp quyền), rồi "/qr-login/approve" sau khi người dùng xác nhận bằng biometric/nhập
 *    lại mật khẩu trên chính điện thoại. Có thêm "/qr-login/cancel" nếu từ chối xác nhận.
 * Vì QR của 2 luồng có định dạng khác nhau hẳn (URL thuần vs JSON), người dùng phải tự chọn
 * đúng nút quét tương ứng với UI đang hiển thị trên thiết bị kia.
 */
enum class QrLoginMode {
    LEGACY,
    KEYCLOAK_SPI,
}

class QrLoginApi {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json".toMediaType()

    // Chỉ KEYCLOAK_SPI dùng — LEGACY không gọi API nào của app này (xem doc comment ở trên).
    suspend fun scan(apiUrl: String, sessionId: String, accessToken: String): Result<Unit> =
        postSessionAction("$apiUrl/qr-login/scan", QrApproveRequest(session_id = sessionId, access_token = accessToken))

    suspend fun approve(apiUrl: String, sessionId: String, accessToken: String): Result<Unit> =
        postSessionAction("$apiUrl/qr-login/approve", QrApproveRequest(session_id = sessionId, access_token = accessToken))

    // Người dùng bấm "Từ chối" hoặc biometric thất bại — trả phiên QR về trạng thái chờ
    // quét lại thay vì để thiết bị kia bị treo mãi ở "đã quét, chờ xác nhận".
    suspend fun cancel(apiUrl: String, sessionId: String, accessToken: String): Result<Unit> =
        postSessionAction("$apiUrl/qr-login/cancel", QrApproveRequest(session_id = sessionId, access_token = accessToken))

    private suspend fun postSessionAction(url: String, requestBody: QrApproveRequest): Result<Unit> {
        val body = json.encodeToString(QrApproveRequest.serializer(), requestBody)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${requestBody.access_token}")
            .post(body.toRequestBody(jsonMediaType))
            .build()

        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string().orEmpty()
                        Result.failure(IOException("HTTP ${response.code}: $errorBody"))
                    } else {
                        Result.success(Unit)
                    }
                }
            } catch (e: IOException) {
                Result.failure(e)
            }
        }
    }
}

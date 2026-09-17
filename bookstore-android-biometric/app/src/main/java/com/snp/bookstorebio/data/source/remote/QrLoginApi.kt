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
 * Có 2 nguồn QR song song, khác endpoint và khác số bước approve:
 *  - LEGACY: QR vẽ bởi bookstore-fe-qr (web thường ngoài Keycloak) → bookstore-api-qr,
 *    route "/qr/approve" (Node.js, giữ client_secret của qr-login-confidential) — 1 bước,
 *    approve ngay khi quét xong, KHÔNG có bước xác nhận thêm trên điện thoại.
 *  - KEYCLOAK_SPI: QR vẽ bởi qr-login.ftl (ngay trên trang login mặc định của Keycloak,
 *    qua "Try another way") → chạy thẳng trong Keycloak (keycloak-spi-qr-login) — 2 bước:
 *    "/qr-login/scan" ngay khi quét (chỉ ghi nhận, CHƯA cấp quyền), rồi "/qr-login/approve"
 *    sau khi người dùng xác nhận bằng biometric/nhập lại mật khẩu trên chính điện thoại.
 *    Có thêm "/qr-login/cancel" nếu người dùng từ chối xác nhận.
 * Cả hai cùng định dạng JSON {"apiUrl":..., "sessionId":...} nên người dùng phải tự chọn
 * đúng nút quét tương ứng với UI đang hiển thị trên thiết bị kia — apiUrl chỉ khác base URL
 * (bookstore-api-qr vs Keycloak realm) nên không tự suy ra chắc chắn 100% được.
 */
enum class QrLoginMode {
    LEGACY,
    KEYCLOAK_SPI,
}

class QrLoginApi {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json".toMediaType()

    // Chỉ KEYCLOAK_SPI có bước xác nhận riêng (scan trước, approve sau) — LEGACY dùng thẳng
    // [approve] 1 bước như cũ vì bookstore-api-qr (Node.js) không có route "/qr/scan".
    suspend fun scan(apiUrl: String, sessionId: String, accessToken: String): Result<Unit> =
        postSessionAction("$apiUrl/qr-login/scan", QrApproveRequest(session_id = sessionId, access_token = accessToken))

    // LEGACY: bookstore-api-qr forward access_token/refresh_token/expires_in/scope thẳng cho
    // web dùng luôn (nó không tự issue token) nên cần đủ các field này trong body.
    // KEYCLOAK_SPI: chỉ cần session_id + access_token, các field còn lại bị bỏ qua ở server.
    suspend fun approve(
        mode: QrLoginMode,
        apiUrl: String,
        sessionId: String,
        accessToken: String,
        refreshToken: String?,
        expiresIn: Int?,
        scope: String?,
    ): Result<Unit> {
        val path = if (mode == QrLoginMode.LEGACY) "/qr/approve" else "/qr-login/approve"
        val request = QrApproveRequest(
            session_id = sessionId,
            access_token = accessToken,
            refresh_token = refreshToken,
            expires_in = expiresIn,
            token_type = "Bearer",
            scope = scope,
        )
        return postSessionAction("$apiUrl$path", request)
    }

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

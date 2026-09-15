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
 * Có 2 nguồn QR song song, khác endpoint approve:
 *  - LEGACY: QR vẽ bởi bookstore-fe-qr (web thường ngoài Keycloak) → bookstore-api-qr,
 *    route "/qr/approve" (Node.js, giữ client_secret của qr-login-confidential).
 *  - KEYCLOAK_SPI: QR vẽ bởi qr-login.ftl (ngay trên trang login mặc định của Keycloak,
 *    qua "Try another way") → chạy thẳng trong Keycloak, route "/qr-login/approve"
 *    (keycloak-spi-qr-login, dùng TokenManager nội bộ để issue token).
 * Cả hai cùng định dạng JSON {"apiUrl":..., "sessionId":...} nên người dùng phải tự chọn
 * đúng nút quét tương ứng với UI đang hiển thị trên thiết bị kia — apiUrl chỉ khác base URL
 * (bookstore-api-qr vs Keycloak realm) nên không tự suy ra chắc chắn 100% được.
 */
enum class QrLoginMode(val approvePath: String) {
    LEGACY("/qr/approve"),
    KEYCLOAK_SPI("/qr-login/approve"),
}

class QrLoginApi {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json".toMediaType()

    suspend fun approve(
        mode: QrLoginMode,
        apiUrl: String,
        sessionId: String,
        accessToken: String,
        refreshToken: String?,
        expiresIn: Int?,
        scope: String?,
    ): Result<Unit> {
        val body = json.encodeToString(
            QrApproveRequest.serializer(),
            QrApproveRequest(
                session_id = sessionId,
                access_token = accessToken,
                refresh_token = refreshToken,
                expires_in = expiresIn,
                token_type = "Bearer",
                scope = scope,
            )
        )
        val request = Request.Builder()
            .url("$apiUrl${mode.approvePath}")
            .header("Authorization", "Bearer $accessToken")
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

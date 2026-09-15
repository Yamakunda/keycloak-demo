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
 * Gọi endpoint REST SPI chạy ngay trong Keycloak (/realms/{realm}/qr-login/approve) để tự
 * động "approve" phiên đăng nhập QR hiện trên trang login Keycloak, bằng access_token app
 * đã có sẵn (từ Authorization Code + PKCE lúc đăng nhập đầu) — không cần mở Custom Tabs hay
 * đăng nhập lại. apiUrl lấy từ chính nội dung mã QR (chính là base URL của realm) nên không
 * cần hardcode BuildConfig ở đây.
 */
class QrLoginApi {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json".toMediaType()

    suspend fun approve(
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
            .url("$apiUrl/qr-login/approve")
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

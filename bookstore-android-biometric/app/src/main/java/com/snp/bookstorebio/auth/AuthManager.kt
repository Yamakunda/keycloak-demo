package com.snp.bookstorebio.auth

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.snp.bookstorebio.BuildConfig
import net.openid.appauth.AppAuthConfiguration
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenRequest
import net.openid.appauth.TokenResponse
import net.openid.appauth.connectivity.ConnectionBuilder
import java.net.HttpURLConnection
import java.net.URL

/**
 * AppAuth mặc định (DefaultConnectionBuilder) chặn cứng mọi kết nối không phải HTTPS ở bước
 * đổi code/refresh_token lấy token — kể cả khi Manifest đã bật usesCleartextTraffic. Vì demo
 * này trỏ thẳng IP LAN (http://192.168.x.x:8080) thay vì ngrok HTTPS, cần ConnectionBuilder
 * riêng bỏ qua check đó. KHÔNG dùng cho build release trỏ backend thật ngoài LAN.
 */
private object CleartextConnectionBuilder : ConnectionBuilder {
    override fun openConnection(uri: android.net.Uri): HttpURLConnection {
        val connection = URL(uri.toString()).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.instanceFollowRedirects = false
        return connection
    }
}

/**
 * Bọc AppAuth cho luồng "đăng nhập 1 lần, unlock bằng vân tay sau đó":
 * - buildLoginIntent/handleAuthorizationResponse: CHỈ dùng ở lần đăng nhập đầu (mở Custom
 *   Tab, username/password qua Keycloak, xin thêm scope offline_access để có refresh_token
 *   sống lâu).
 * - exchangeRefreshToken: dùng lại refresh_token đã lưu (giải mã bằng vân tay qua
 *   BiometricVault) để lấy access_token mới, KHÔNG mở Custom Tab, không cần mạng browser.
 */
class AuthManager(context: Context) {

    private val redirectUri = "com.snp.bookstorebio:/oauth2redirect".toUri()
    private val service = AuthorizationService(
        context,
        AppAuthConfiguration.Builder()
            .setConnectionBuilder(CleartextConnectionBuilder)
            .build(),
    )

    private val serviceConfig = AuthorizationServiceConfiguration(
        "${BuildConfig.KEYCLOAK_BASE_URL}/realms/${BuildConfig.KEYCLOAK_REALM}/protocol/openid-connect/auth".toUri(),
        "${BuildConfig.KEYCLOAK_BASE_URL}/realms/${BuildConfig.KEYCLOAK_REALM}/protocol/openid-connect/token".toUri(),
        null,
        "${BuildConfig.KEYCLOAK_BASE_URL}/realms/${BuildConfig.KEYCLOAK_REALM}/protocol/openid-connect/logout".toUri(),
    )

    fun buildLoginIntent(): Intent {
        val request = AuthorizationRequest.Builder(
            serviceConfig,
            BuildConfig.KEYCLOAK_CLIENT_ID,
            ResponseTypeValues.CODE,
            redirectUri,
        )
            // offline_access -> Keycloak cấp refresh_token sống lâu (client.offline.session.*
            // = 30 ngày, xem test-realm.json client "biometric-demo"), không phụ thuộc
            // vòng đời SSO session ngắn của access token thường.
            .setScope("openid profile email offline_access")
            .build()
        return service.getAuthorizationRequestIntent(request)
    }

    /** Gọi trong onActivityResult/ActivityResultLauncher sau khi Custom Tab redirect về app. */
    fun handleAuthorizationResponse(
        intent: Intent,
        onResult: (AuthState?, AuthorizationException?) -> Unit,
    ) {
        val response = AuthorizationResponse.fromIntent(intent)
        val error = AuthorizationException.fromIntent(intent)

        if (response == null) {
            onResult(null, error)
            return
        }

        // Đổi authorization code lấy token — request PKCE code_verifier đã được AppAuth
        // tự lưu kèm response, không cần tự quản lý.
        service.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, exception ->
            if (tokenResponse != null) {
                val state = AuthState(response, exception)
                state.update(tokenResponse, exception)
                onResult(state, null)
            } else {
                onResult(null, exception)
            }
        }
    }

    /**
     * Đổi refresh_token (đã giải mã bằng vân tay từ BiometricVault) lấy access_token mới —
     * gọi thẳng token endpoint, KHÔNG mở Custom Tab. Đây là bước thay thế cho việc phải
     * đăng nhập lại mỗi lần mở app.
     */
    fun exchangeRefreshToken(
        refreshToken: String,
        onResult: (TokenResponse?, AuthorizationException?) -> Unit,
    ) {
        val request = TokenRequest.Builder(serviceConfig, BuildConfig.KEYCLOAK_CLIENT_ID)
            .setGrantType("refresh_token")
            .setRefreshToken(refreshToken)
            .setScopes("openid profile email offline_access")
            .build()
        service.performTokenRequest(request, onResult)
    }

    fun buildLogoutIntent(idToken: String?): Intent {
        val params = mutableMapOf(
            "post_logout_redirect_uri" to redirectUri.toString(),
        )
        idToken?.let { params["id_token_hint"] = it }
        val uri = "${BuildConfig.KEYCLOAK_BASE_URL}/realms/${BuildConfig.KEYCLOAK_REALM}/protocol/openid-connect/logout"
            .toUri()
            .buildUpon()
            .apply { params.forEach { (k, v) -> appendQueryParameter(k, v) } }
            .build()
        return Intent(Intent.ACTION_VIEW, uri)
    }

    fun dispose() {
        service.dispose()
    }
}

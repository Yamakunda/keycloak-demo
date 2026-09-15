package com.snp.bookstore.data.source.remote

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.snp.bookstore.BuildConfig
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues

/**
 * Bọc AppAuth: mở Custom Tab tới Keycloak (Authorization Code + PKCE, client public
 * "passwordless-demo"), nhận redirect qua RedirectUriReceiverActivity, đổi code lấy token.
 * Toàn bộ diễn ra trong trình duyệt thật nên WebAuthn/passkey của Keycloak hoạt động bình thường.
 */
class AuthManager(context: Context) {

    private val redirectUri = "com.snp.bookstore:/oauth2redirect".toUri()
    private val service = AuthorizationService(context)

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
            .setScope("openid profile email")
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

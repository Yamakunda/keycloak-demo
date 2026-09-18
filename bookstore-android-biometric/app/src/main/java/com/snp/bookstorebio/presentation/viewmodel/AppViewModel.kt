package com.snp.bookstorebio.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snp.bookstorebio.data.source.local.BiometricVault
import com.snp.bookstorebio.data.source.remote.AuthManager
import com.snp.bookstorebio.data.source.remote.QrLoginApi
import com.snp.bookstorebio.data.source.remote.QrLoginMode
import com.snp.bookstorebio.domain.model.Book
import com.snp.bookstorebio.domain.usecase.GetBooksUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import net.openid.appauth.AuthState
import javax.inject.Inject

sealed interface QrApproveResult {
    data object Approving : QrApproveResult
    data object Success : QrApproveResult
    data class Failed(val message: String) : QrApproveResult
}

// Chỉ phát sinh cho KEYCLOAK_SPI: đã gọi /qr-login/scan xong (danh tính đã ghi nhận) nhưng
// CHƯA gọi /qr-login/approve — chờ người dùng xác nhận bằng biometric/nhập lại mật khẩu
// trên chính điện thoại trước khi thật sự cấp quyền cho thiết bị kia.
data class PendingQrConfirmation(
    val apiUrl: String,
    val sessionId: String,
)

sealed interface UiState {
    data class LoggedOut(val savedUsername: String? = null) : UiState
    data object LoggingIn : UiState
    data class LoggedIn(
        val username: String,
        val books: List<Book> = emptyList(),
        val loadingBooks: Boolean = false,
        val error: String? = null,
        val biometricEnabled: Boolean = false,
        // Đang mở màn hình quét QR để đăng nhập chéo thiết bị (không đăng xuất khỏi phiên
        // hiện tại) — null nghĩa là không quét; khác null cho biết đang quét cho nguồn QR
        // nào (web thường hay trang login Keycloak) để gọi đúng endpoint approve.
        val scanningQrMode: QrLoginMode? = null,
        val qrApproveResult: QrApproveResult? = null,
        val pendingQrConfirmation: PendingQrConfirmation? = null,
        // Chỉ dùng cho LEGACY (Device Authorization Grant): URL xác nhận (verification_uri_complete)
        // cần mở bằng Custom Tabs sau khi quét — MainActivity quan sát field này để launch Intent,
        // ViewModel không tự mở Activity được.
        val urlToOpen: String? = null,
    ) : UiState
}

@HiltViewModel
class AppViewModel @Inject constructor(
    val authManager: AuthManager,
    val biometricVault: BiometricVault,
    private val getBooksUseCase: GetBooksUseCase,
    private val qrLoginApi: QrLoginApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.LoggedOut())
    val uiState: StateFlow<UiState> = _uiState

    private var authState: AuthState? = null

    init {
        _uiState.value = UiState.LoggedOut(savedUsername = usernameWithUsableVault())
    }

    private fun usernameWithUsableVault(): String? {
        val lastUsername = biometricVault.lastUsername() ?: return null
        return lastUsername.takeIf {
            biometricVault.isBiometricEnabled(it) && biometricVault.hasStoredToken(it)
        }
    }

    fun onLoginStarted() {
        _uiState.value = UiState.LoggingIn
    }

    fun onFirstLoginResult(state: AuthState?) {
        if (state == null) {
            _uiState.value = UiState.LoggedOut(savedUsername = usernameWithUsableVault())
            return
        }
        authState = state
        val username = state.idToken?.let { decodePreferredUsername(it) } ?: "user"

        val previousUsername = biometricVault.lastUsername()
        if (previousUsername != null && previousUsername != username) {
            biometricVault.clear(previousUsername)
        }

        _uiState.value = UiState.LoggedIn(
            username = username,
            biometricEnabled = biometricVault.isBiometricEnabled(username),
        )
        loadBooks()
    }

    fun pendingRefreshTokenToSave(): String? = authState?.refreshToken

    fun currentUsername(): String? = (_uiState.value as? UiState.LoggedIn)?.username

    fun onSavedToVault() {
        val current = (_uiState.value as? UiState.LoggedIn) ?: return
        _uiState.value = current.copy(biometricEnabled = true)
    }

    fun onBiometricDisabled() {
        val current = (_uiState.value as? UiState.LoggedIn) ?: return
        biometricVault.clear(current.username)
        _uiState.value = current.copy(biometricEnabled = false)
    }

    fun onScanQrClicked(mode: QrLoginMode) {
        val current = (_uiState.value as? UiState.LoggedIn) ?: return
        _uiState.value = current.copy(scanningQrMode = mode)
    }

    fun onQrScanDismissed() {
        val current = (_uiState.value as? UiState.LoggedIn) ?: return
        _uiState.value = current.copy(scanningQrMode = null)
    }

    fun onQrApproveResultDismissed() {
        val current = (_uiState.value as? UiState.LoggedIn) ?: return
        _uiState.value = current.copy(qrApproveResult = null)
    }

    /**
     * Hai nguồn QR khác hẳn nhau về bản chất, chọn theo mode người dùng đã bấm nút trước khi
     * quét:
     * - LEGACY: QR encode thẳng "verification_uri_complete" của OAuth 2.0 Device Authorization
     *   Grant (RFC 8628, xem bookstore-fe-qr) — app KHÔNG gửi access_token đi đâu cả, chỉ mở
     *   URL đó bằng Custom Tabs (dùng chung cookie SSO đã đăng nhập) để người dùng tự bấm
     *   "Cho phép" trên chính trang xác nhận của Keycloak. MainActivity quan sát [urlToOpen]
     *   để launch Custom Tabs Intent.
     * - KEYCLOAK_SPI: QR chứa JSON {"apiUrl", "sessionId"} riêng của SPI — gọi /qr-login/scan
     *   để ghi nhận danh tính, rồi chuyển sang màn hình chờ xác nhận (pendingQrConfirmation)
     *   — CHƯA cấp quyền cho thiết bị kia. Người dùng phải xác nhận bằng biometric/nhập lại
     *   mật khẩu (xem MainActivity.promptConfirmQrApprove) thì [onQrApproveConfirmed] mới
     *   thật sự gọi /qr-login/approve.
     */
    fun onQrCodeScanned(rawValue: String) {
        val current = (_uiState.value as? UiState.LoggedIn) ?: return
        val mode = current.scanningQrMode ?: return

        if (mode == QrLoginMode.LEGACY) {
            if (!rawValue.startsWith("http://") && !rawValue.startsWith("https://")) {
                _uiState.value = current.copy(
                    scanningQrMode = null,
                    qrApproveResult = QrApproveResult.Failed("Mã QR không hợp lệ."),
                )
                return
            }
            _uiState.value = current.copy(scanningQrMode = null, urlToOpen = rawValue)
            return
        }

        val accessToken = authState?.accessToken
        if (accessToken == null) {
            _uiState.value = current.copy(
                scanningQrMode = null,
                qrApproveResult = QrApproveResult.Failed("Phiên đăng nhập đã hết hạn, hãy đăng nhập lại."),
            )
            return
        }

        val parsed = runCatching {
            val obj = Json.parseToJsonElement(rawValue).jsonObject
            val apiUrl = obj["apiUrl"]?.jsonPrimitive?.content ?: error("Thiếu apiUrl")
            val sessionId = obj["sessionId"]?.jsonPrimitive?.content ?: error("Thiếu sessionId")
            apiUrl to sessionId
        }.getOrNull()

        if (parsed == null) {
            _uiState.value = current.copy(
                scanningQrMode = null,
                qrApproveResult = QrApproveResult.Failed("Mã QR không hợp lệ."),
            )
            return
        }
        val (apiUrl, sessionId) = parsed

        // KEYCLOAK_SPI: gọi /scan trước, chưa cấp quyền — chờ xác nhận biometric.
        _uiState.value = current.copy(scanningQrMode = null, qrApproveResult = QrApproveResult.Approving)
        viewModelScope.launch {
            val result = qrLoginApi.scan(apiUrl = apiUrl, sessionId = sessionId, accessToken = accessToken)
            val latest = (_uiState.value as? UiState.LoggedIn) ?: return@launch
            _uiState.value = result.fold(
                onSuccess = {
                    latest.copy(
                        qrApproveResult = null,
                        pendingQrConfirmation = PendingQrConfirmation(apiUrl = apiUrl, sessionId = sessionId),
                    )
                },
                onFailure = { e -> latest.copy(qrApproveResult = QrApproveResult.Failed(e.message ?: "Lỗi không xác định")) },
            )
        }
    }

    /** MainActivity gọi ngay sau khi đã launch Custom Tabs Intent cho [urlToOpen]. */
    fun onUrlOpened() {
        val current = (_uiState.value as? UiState.LoggedIn) ?: return
        _uiState.value = current.copy(urlToOpen = null)
    }

    fun onQrConfirmationDismissed() {
        val current = (_uiState.value as? UiState.LoggedIn) ?: return
        val pending = current.pendingQrConfirmation ?: return
        _uiState.value = current.copy(pendingQrConfirmation = null)

        val accessToken = authState?.accessToken ?: return
        viewModelScope.launch {
            qrLoginApi.cancel(apiUrl = pending.apiUrl, sessionId = pending.sessionId, accessToken = accessToken)
        }
    }

    /** Gọi SAU KHI BiometricPrompt xác nhận thành công — đây mới là bước cấp quyền thật sự. */
    fun onQrApproveConfirmed() {
        val current = (_uiState.value as? UiState.LoggedIn) ?: return
        val pending = current.pendingQrConfirmation ?: return
        val accessToken = authState?.accessToken
        if (accessToken == null) {
            _uiState.value = current.copy(
                pendingQrConfirmation = null,
                qrApproveResult = QrApproveResult.Failed("Phiên đăng nhập đã hết hạn, hãy đăng nhập lại."),
            )
            return
        }

        _uiState.value = current.copy(pendingQrConfirmation = null, qrApproveResult = QrApproveResult.Approving)
        viewModelScope.launch {
            val result = qrLoginApi.approve(
                apiUrl = pending.apiUrl,
                sessionId = pending.sessionId,
                accessToken = accessToken,
            )
            val latest = (_uiState.value as? UiState.LoggedIn) ?: return@launch
            _uiState.value = result.fold(
                onSuccess = { latest.copy(qrApproveResult = QrApproveResult.Success) },
                onFailure = { e -> latest.copy(qrApproveResult = QrApproveResult.Failed(e.message ?: "Lỗi không xác định")) },
            )
        }
    }

    fun onBiometricUnavailable(message: String) {
        val current = (_uiState.value as? UiState.LoggedIn) ?: return
        _uiState.value = current.copy(error = message, biometricEnabled = false)
    }

    fun onBiometricUnlocked(username: String, refreshToken: String) {
        authManager.exchangeRefreshToken(refreshToken) { tokenResponse, exception ->
            if (tokenResponse == null) {
                biometricVault.clear(username)
                _uiState.value = UiState.LoggedOut()
                return@exchangeRefreshToken
            }
            val newAuthState = AuthState().apply { update(tokenResponse, exception) }
            authState = newAuthState
            val resolvedUsername = newAuthState.idToken?.let { decodePreferredUsername(it) } ?: username
            _uiState.value = UiState.LoggedIn(username = resolvedUsername, biometricEnabled = true)
            loadBooks()
        }
    }

    fun onVaultInvalidated(username: String) {
        biometricVault.clear(username)
        _uiState.value = UiState.LoggedOut()
    }

    fun loadBooks() {
        val token = authState?.accessToken ?: return
        val current = (_uiState.value as? UiState.LoggedIn) ?: return
        _uiState.value = current.copy(loadingBooks = true, error = null)

        viewModelScope.launch {
            val result = getBooksUseCase(token)
            val latest = (_uiState.value as? UiState.LoggedIn) ?: return@launch
            result.fold(
                onSuccess = { books ->
                    _uiState.value = latest.copy(
                        books = books,
                        loadingBooks = false,
                    )
                },
                onFailure = { e ->
                    _uiState.value = latest.copy(loadingBooks = false, error = e.message)
                }
            )
        }
    }

    fun idTokenForLogout(): String? = authState?.idToken

    fun onLoggedOut() {
        authState = null
        _uiState.value = UiState.LoggedOut(savedUsername = usernameWithUsableVault())
    }

    override fun onCleared() {
        authManager.dispose()
    }
}

private fun decodePreferredUsername(idToken: String): String? = runCatching {
    var payload = idToken.split(".")[1]
    val padding = (4 - (payload.length % 4)) % 4
    payload += "=".repeat(padding)
    val decoded = String(android.util.Base64.decode(payload, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP))
    Regex("\"preferred_username\"\\s*:\\s*\"([^\"]+)\"").find(decoded)?.groupValues?.get(1)
}.getOrNull()

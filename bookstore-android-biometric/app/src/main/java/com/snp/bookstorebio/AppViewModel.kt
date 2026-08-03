package com.snp.bookstorebio

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.snp.bookstorebio.auth.AuthManager
import com.snp.bookstorebio.auth.BiometricVault
import com.snp.bookstorebio.model.Book
import com.snp.bookstorebio.network.BookstoreApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthState

sealed interface UiState {
    /** Chưa từng đăng nhập — hoặc vault đã bị xoá (logout / key Keystore bị huỷ). */
    data object LoggedOut : UiState

    /** Đang mở Custom Tab để đăng nhập lần đầu bằng username/password. */
    data object LoggingIn : UiState

    /** Đã có refresh_token trong vault từ trước — chờ user quét vân tay để unlock. */
    data object LockedBiometric : UiState

    data class LoggedIn(
        val username: String,
        val books: List<Book> = emptyList(),
        val loadingBooks: Boolean = false,
        val error: String? = null,
    ) : UiState
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    val authManager = AuthManager(application)
    val biometricVault = BiometricVault(application)
    private val api = BookstoreApi()

    private val _uiState = MutableStateFlow<UiState>(UiState.LoggedOut)
    val uiState: StateFlow<UiState> = _uiState

    private var authState: AuthState? = null

    init {
        // Có refresh_token đã lưu từ lần đăng nhập trước -> chỉ cần vân tay, không mở Custom Tab.
        _uiState.value = if (biometricVault.hasStoredToken()) UiState.LockedBiometric else UiState.LoggedOut
    }

    fun onLoginStarted() {
        _uiState.value = UiState.LoggingIn
    }

    /** Kết quả từ Custom Tab (lần đăng nhập ĐẦU TIÊN bằng username/password). */
    fun onFirstLoginResult(state: AuthState?) {
        if (state == null) {
            _uiState.value = UiState.LoggedOut
            return
        }
        authState = state
        val username = state.idToken?.let { decodePreferredUsername(it) } ?: "user"
        _uiState.value = UiState.LoggedIn(username = username)
        loadBooks()
        // Lưu refresh_token vào vault do MainActivity gọi tiếp sau khi có Cipher đã unlock
        // (xem MainActivity.promptSaveToVault) — ViewModel chỉ giữ authState ở đây.
    }

    fun pendingRefreshTokenToSave(): String? = authState?.refreshToken

    fun onSavedToVault() {
        // Không đổi UI state — user đã LoggedIn, chỉ là refresh_token giờ có thêm bản mã hoá.
    }

    /**
     * Sau khi BiometricPrompt unlock thành công và giải mã được refresh_token đã lưu.
     *
     * Lưu ý: realm "test" cấu hình revokeRefreshToken=false (test-realm.json) nên Keycloak
     * KHÔNG rotate refresh_token mỗi lần dùng — token cũ trong vault vẫn tái sử dụng được
     * vô thời hạn (trong vòng đời offline session 30 ngày). Nếu bật rotation sau này, cần
     * re-encrypt token mới vào vault ở đây — nhưng việc đó đòi hỏi xin vân tay thêm lần nữa
     * (Cipher ENCRYPT cũng cần setUserAuthenticationRequired), nên demo này cố tình bỏ qua.
     */
    fun onBiometricUnlocked(refreshToken: String) {
        authManager.exchangeRefreshToken(refreshToken) { tokenResponse, exception ->
            if (tokenResponse == null) {
                // refresh_token hết hạn/bị revoke ở server -> phải đăng nhập lại từ đầu
                biometricVault.clear()
                _uiState.value = UiState.LoggedOut
                return@exchangeRefreshToken
            }
            val newAuthState = AuthState().apply { update(tokenResponse, exception) }
            authState = newAuthState
            val username = newAuthState.idToken?.let { decodePreferredUsername(it) } ?: "user"
            _uiState.value = UiState.LoggedIn(username = username)
            loadBooks()
        }
    }

    /** Key Keystore đã bị Android huỷ (đổi vân tay trên máy) -> vault vô dụng, phải đăng nhập lại. */
    fun onVaultInvalidated() {
        biometricVault.clear()
        _uiState.value = UiState.LoggedOut
    }

    fun loadBooks() {
        val token = authState?.accessToken ?: return
        val current = (_uiState.value as? UiState.LoggedIn) ?: return
        _uiState.value = current.copy(loadingBooks = true, error = null)

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { api.fetchBooks(token) }
            val latest = (_uiState.value as? UiState.LoggedIn) ?: return@launch
            result.fold(
                onSuccess = { resp ->
                    _uiState.value = latest.copy(
                        books = resp.books,
                        loadingBooks = false,
                        username = resp.authenticatedAs ?: latest.username,
                    )
                },
            ) { e ->
                _uiState.value = latest.copy(loadingBooks = false, error = e.message)
            }
        }
    }

    fun idTokenForLogout(): String? = authState?.idToken

    /** Logout thật: xoá cả session Keycloak lẫn refresh_token trong vault thiết bị. */
    fun onLoggedOut() {
        authState = null
        biometricVault.clear()
        _uiState.value = UiState.LoggedOut
    }

    override fun onCleared() {
        authManager.dispose()
    }
}

/** Giải mã payload JWT (base64url, không verify — chỉ để hiển thị username, verify thật do server làm) */
private fun decodePreferredUsername(idToken: String): String? = runCatching {
    var payload = idToken.split(".")[1]
    val padding = (4 - (payload.length % 4)) % 4
    payload += "=".repeat(padding)
    val decoded = String(android.util.Base64.decode(payload, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP))
    Regex("\"preferred_username\"\\s*:\\s*\"([^\"]+)\"").find(decoded)?.groupValues?.get(1)
}.getOrNull()

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
    /**
     * Chưa đăng nhập. Nếu [savedUsername] khác null, trên máy đã có vault vân tay còn hiệu
     * lực của đúng tài khoản đó (đăng xuất không xoá vault) — màn Login sẽ hiện thêm nút
     * "Đăng nhập bằng vân tay" cho tài khoản này, bên cạnh nút đăng nhập Keycloak bình thường.
     */
    data class LoggedOut(val savedUsername: String? = null) : UiState

    /** Đang mở Custom Tab để đăng nhập lần đầu bằng username/password. */
    data object LoggingIn : UiState

    data class LoggedIn(
        val username: String,
        val books: List<Book> = emptyList(),
        val loadingBooks: Boolean = false,
        val error: String? = null,
        val biometricEnabled: Boolean = false,
    ) : UiState
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    val authManager = AuthManager(application)
    val biometricVault = BiometricVault(application)
    private val api = BookstoreApi()

    private val _uiState = MutableStateFlow<UiState>(UiState.LoggedOut())
    val uiState: StateFlow<UiState> = _uiState

    private var authState: AuthState? = null

    init {
        _uiState.value = UiState.LoggedOut(savedUsername = usernameWithUsableVault())
    }

    /** Tài khoản đăng nhập gần nhất trên máy có vault còn hiệu lực để unlock bằng vân tay, nếu có. */
    private fun usernameWithUsableVault(): String? {
        val lastUsername = biometricVault.lastUsername() ?: return null
        return lastUsername.takeIf {
            biometricVault.isBiometricEnabled(it) && biometricVault.hasStoredToken(it)
        }
    }

    fun onLoginStarted() {
        _uiState.value = UiState.LoggingIn
    }

    /** Kết quả từ Custom Tab (lần đăng nhập ĐẦU TIÊN bằng username/password). */
    fun onFirstLoginResult(state: AuthState?) {
        if (state == null) {
            _uiState.value = UiState.LoggedOut(savedUsername = usernameWithUsableVault())
            return
        }
        authState = state
        val username = state.idToken?.let { decodePreferredUsername(it) } ?: "user"

        // Chỉ giữ TỐI ĐA 1 vault vân tay tại một thời điểm trên máy: đăng nhập một tài khoản
        // KHÁC với tài khoản đang có vault sẽ xoá hẳn vault cũ, không giữ song song nhiều
        // tài khoản (khác last_username nghĩa là account cũ, nếu có, không dùng được nữa).
        val previousUsername = biometricVault.lastUsername()
        if (previousUsername != null && previousUsername != username) {
            biometricVault.clear(previousUsername)
        }

        _uiState.value = UiState.LoggedIn(
            username = username,
            biometricEnabled = biometricVault.isBiometricEnabled(username),
        )
        loadBooks()
        // Nếu tài khoản này đã từng bật vân tay trước đây trên máy, MainActivity sẽ tự
        // prompt lưu vault ngay sau lần đăng nhập đầu này (xem loginLauncher).
    }

    fun pendingRefreshTokenToSave(): String? = authState?.refreshToken

    fun currentUsername(): String? = (_uiState.value as? UiState.LoggedIn)?.username

    fun onSavedToVault() {
        val current = (_uiState.value as? UiState.LoggedIn) ?: return
        _uiState.value = current.copy(biometricEnabled = true)
    }

    /** User bấm tắt trong Settings — xoá vault của CHÍNH tài khoản đang đăng nhập, không cần vân tay để tắt. */
    fun onBiometricDisabled() {
        val current = (_uiState.value as? UiState.LoggedIn) ?: return
        biometricVault.clear(current.username)
        _uiState.value = current.copy(biometricEnabled = false)
    }

    /** Thiết bị chưa đăng ký vân tay/Face/PIN nào — không thể bật, chỉ báo lỗi, không đổi toggle. */
    fun onBiometricUnavailable(message: String) {
        val current = (_uiState.value as? UiState.LoggedIn) ?: return
        _uiState.value = current.copy(error = message, biometricEnabled = false)
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
    fun onBiometricUnlocked(username: String, refreshToken: String) {
        authManager.exchangeRefreshToken(refreshToken) { tokenResponse, exception ->
            if (tokenResponse == null) {
                // refresh_token hết hạn/bị revoke ở server -> phải đăng nhập lại từ đầu
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

    /** Key Keystore đã bị Android huỷ (đổi vân tay trên máy) -> vault của tài khoản này vô dụng. */
    fun onVaultInvalidated(username: String) {
        biometricVault.clear(username)
        _uiState.value = UiState.LoggedOut()
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

    /**
     * Logout: kết thúc session Keycloak hiện tại, nhưng KHÔNG xoá vault vân tay của tài
     * khoản này — giống các app thực tế (banking...), để lần sau đăng nhập lại đúng tài
     * khoản này trên cùng thiết bị vẫn dùng được vân tay ngay, không cần bật lại từ đầu.
     * Muốn quên vân tay hẳn, user tắt toggle trong màn Books trước khi đăng xuất.
     */
    fun onLoggedOut() {
        authState = null
        _uiState.value = UiState.LoggedOut(savedUsername = usernameWithUsableVault())
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

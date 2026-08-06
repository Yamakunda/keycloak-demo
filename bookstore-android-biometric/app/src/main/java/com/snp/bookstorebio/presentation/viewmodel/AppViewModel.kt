package com.snp.bookstorebio.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snp.bookstorebio.data.source.local.BiometricVault
import com.snp.bookstorebio.data.source.remote.AuthManager
import com.snp.bookstorebio.domain.model.Book
import com.snp.bookstorebio.domain.usecase.GetBooksUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import net.openid.appauth.AuthState
import javax.inject.Inject

sealed interface UiState {
    data class LoggedOut(val savedUsername: String? = null) : UiState
    data object LoggingIn : UiState
    data class LoggedIn(
        val username: String,
        val books: List<Book> = emptyList(),
        val loadingBooks: Boolean = false,
        val error: String? = null,
        val biometricEnabled: Boolean = false,
    ) : UiState
}

@HiltViewModel
class AppViewModel @Inject constructor(
    val authManager: AuthManager,
    val biometricVault: BiometricVault,
    private val getBooksUseCase: GetBooksUseCase
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

package com.snp.bookstore.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snp.bookstore.data.source.local.TokenStore
import com.snp.bookstore.data.source.remote.AuthManager
import com.snp.bookstore.domain.model.Book
import com.snp.bookstore.domain.usecase.GetBooksUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import net.openid.appauth.AuthState
import javax.inject.Inject

sealed interface UiState {
    data object LoggedOut : UiState
    data object LoggingIn : UiState
    data class LoggedIn(
        val username: String,
        val books: List<Book> = emptyList(),
        val loadingBooks: Boolean = false,
        val error: String? = null,
    ) : UiState
}

@HiltViewModel
class AppViewModel @Inject constructor(
    val authManager: AuthManager,
    private val tokenStore: TokenStore,
    private val getBooksUseCase: GetBooksUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.LoggedOut)
    val uiState: StateFlow<UiState> = _uiState

    private var authState: AuthState? = null

    init {
        tokenStore.read()?.let { restored ->
            if (restored.isAuthorized) {
                authState = restored
                _uiState.value = UiState.LoggedIn(username = "…")
                loadBooks()
            }
        }
    }

    fun onLoginStarted() {
        _uiState.value = UiState.LoggingIn
    }

    fun onLoginResult(state: AuthState?) {
        if (state == null) {
            _uiState.value = UiState.LoggedOut
            return
        }
        authState = state
        tokenStore.save(state)
        val username = state.idToken?.let { decodePreferredUsername(it) } ?: "user"
        _uiState.value = UiState.LoggedIn(username = username)
        loadBooks()
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
        tokenStore.clear()
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

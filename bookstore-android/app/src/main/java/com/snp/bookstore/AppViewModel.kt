package com.snp.bookstore

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.snp.bookstore.auth.AuthManager
import com.snp.bookstore.auth.TokenStore
import com.snp.bookstore.model.Book
import com.snp.bookstore.network.BookstoreApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthState

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

class AppViewModel(application: Application) : AndroidViewModel(application) {
    val authManager = AuthManager(application)
    private val tokenStore = TokenStore(application)
    private val api = BookstoreApi()

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

package com.snp.bookstore.presentation.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.snp.bookstore.presentation.viewmodel.AppViewModel
import com.snp.bookstore.presentation.viewmodel.UiState
import com.snp.bookstore.ui.theme.BookstoreTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    private val loginLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (data == null) {
            viewModel.onLoginResult(null)
            return@registerForActivityResult
        }
        viewModel.authManager.handleAuthorizationResponse(data) { state, _ ->
            runOnUiThread {
                viewModel.onLoginResult(state)
            }
        }
    }

    // Đăng xuất SSO session ở Keycloak (mở browser xoá cookie), không chờ kết quả trả về —
    // token cục bộ được xoá ngay lập tức ở onLogoutClick bên dưới.
    private val logoutBrowserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BookstoreTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BookstoreApp(
                        viewModel = viewModel,
                        onLoginClick = {
                            viewModel.onLoginStarted()
                            loginLauncher.launch(viewModel.authManager.buildLoginIntent())
                        },
                        onLogoutClick = {
                            val logoutIntent = viewModel.authManager.buildLogoutIntent(viewModel.idTokenForLogout())
                            viewModel.onLoggedOut()
                            logoutBrowserLauncher.launch(logoutIntent)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookstoreApp(
    viewModel: AppViewModel,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Bookstore — Passwordless") }) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                is UiState.LoggedOut -> LoginScreen(onLoginClick)
                is UiState.LoggingIn -> LoadingScreen("Đang mở trang đăng nhập…")
                is UiState.LoggedIn -> BooksScreen(
                    state = s,
                    onRefresh = { viewModel.loadBooks() },
                    onLogout = onLogoutClick
                )
            }
        }
    }
}

@Composable
private fun LoginScreen(onLoginClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Chào mừng đến Bookstore", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Đăng nhập không cần mật khẩu bằng Face ID / vân tay / PIN (WebAuthn passkey)",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onLoginClick) {
            Text("Đăng nhập bằng Passkey")
        }
    }
}

@Composable
private fun LoadingScreen(message: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(message)
    }
}

@Composable
private fun BooksScreen(
    state: UiState.LoggedIn,
    onRefresh: () -> Unit,
    onLogout: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Xin chào, ${state.username}", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        state.error?.let {
            Text("Lỗi: $it", color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }

        if (state.loadingBooks) {
            LoadingScreen("Đang tải danh sách sách…")
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(state.books) { book ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(book.title, style = MaterialTheme.typography.titleSmall)
                        Text("${book.author} · ${book.genre ?: "—"}", style = MaterialTheme.typography.bodySmall)
                        Text("${book.price.toInt()} đ", style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider()
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Column {
            OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                Text("Làm mới")
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                Text("Đăng xuất")
            }
        }
    }
}

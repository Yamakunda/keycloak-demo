package com.snp.bookstorebio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricPrompt
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.snp.bookstorebio.auth.isKeyInvalidated
import com.snp.bookstorebio.ui.theme.BookstoreTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    // Lần đăng nhập ĐẦU: mở Custom Tab, username/password qua Keycloak.
    private val loginLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (data == null) {
            viewModel.onFirstLoginResult(null)
            return@registerForActivityResult
        }
        viewModel.authManager.handleAuthorizationResponse(data) { state, _ ->
            runOnUiThread {
                viewModel.onFirstLoginResult(state)
                if (state != null) promptSaveToVault()
            }
        }
    }

    private val logoutBrowserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    /** Ngay sau đăng nhập lần đầu: yêu cầu vân tay MỘT LẦN để mã hoá và lưu refresh_token. */
    private fun promptSaveToVault() {
        val refreshToken = viewModel.pendingRefreshTokenToSave() ?: return
        val cipher = viewModel.biometricVault.encryptCipher()
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val authedCipher = result.cryptoObject?.cipher ?: return
                    viewModel.biometricVault.saveToken(authedCipher, refreshToken)
                    viewModel.onSavedToVault()
                }
                // Nếu user huỷ, refresh_token vẫn chỉ tồn tại trong bộ nhớ phiên hiện tại —
                // lần sau mở app sẽ phải đăng nhập lại bằng password (không có gì trong vault).
            }
        )
        prompt.authenticate(
            biometricPromptInfo("Lưu đăng nhập", "Xác thực vân tay để lần sau mở app không cần mật khẩu"),
            BiometricPrompt.CryptoObject(cipher)
        )
    }

    /** App vào lại, đã có refresh_token trong vault: xác thực vân tay để giải mã và dùng ngay. */
    private fun promptUnlockVault() {
        val cipher = try {
            viewModel.biometricVault.decryptCipher()
        } catch (e: Exception) {
            if (e.isKeyInvalidated()) viewModel.onVaultInvalidated()
            return
        }
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val authedCipher = result.cryptoObject?.cipher ?: return
                    val refreshToken = viewModel.biometricVault.readToken(authedCipher)
                    viewModel.onBiometricUnlocked(refreshToken)
                }
            }
        )
        prompt.authenticate(
            biometricPromptInfo("Mở khoá Bookstore", "Xác thực vân tay để đăng nhập"),
            BiometricPrompt.CryptoObject(cipher)
        )
    }

    private fun biometricPromptInfo(title: String, subtitle: String) =
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Huỷ")
            .build()

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
                        onUnlockClick = { promptUnlockVault() },
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
    onUnlockClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    // Vào màn LockedBiometric là tự động bật ngay BiometricPrompt, không cần user bấm gì thêm.
    LaunchedEffect(state) {
        if (state is UiState.LockedBiometric) onUnlockClick()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Bookstore — Biometric") }) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                is UiState.LoggedOut -> LoginScreen(onLoginClick)
                is UiState.LoggingIn -> LoadingScreen("Đang mở trang đăng nhập…")
                is UiState.LockedBiometric -> LockedScreen(onUnlockClick)
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
            "Đăng nhập bằng mật khẩu 1 lần — lần sau mở app chỉ cần vân tay",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onLoginClick) {
            Text("Đăng nhập")
        }
    }
}

@Composable
private fun LockedScreen(onUnlockClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Bookstore đã khoá", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("Xác thực vân tay để tiếp tục", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onUnlockClick) {
            Text("Mở khoá bằng vân tay")
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

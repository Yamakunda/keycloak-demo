package com.snp.bookstorebio.presentation.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.snp.bookstorebio.data.source.local.isKeyInvalidated
import com.snp.bookstorebio.data.source.remote.QrLoginMode
import com.snp.bookstorebio.presentation.ui.qr.QrScannerScreen
import com.snp.bookstorebio.presentation.viewmodel.AppViewModel
import com.snp.bookstorebio.presentation.viewmodel.QrApproveResult
import com.snp.bookstorebio.presentation.viewmodel.UiState
import com.snp.bookstorebio.ui.theme.BookstoreTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val viewModel: AppViewModel by viewModels()

    private var awaitingBiometricEnrollment = false

    private val loginLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (data == null) {
            viewModel.onFirstLoginResult(null)
            return@registerForActivityResult
        }
        viewModel.authManager.handleAuthorizationResponse(data) { state, _ ->
            runOnUiThread {
                viewModel.onFirstLoginResult(state)
                val username = viewModel.currentUsername()
                if (state != null && username != null && viewModel.biometricVault.isBiometricEnabled(username)) {
                    startBiometricEnrollmentFlow()
                }
            }
        }
    }

    private val logoutBrowserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    private fun promptSaveToVault() {
        val refreshToken = viewModel.pendingRefreshTokenToSave()
        val username = viewModel.currentUsername()
        if (refreshToken == null || username == null) {
            viewModel.onBiometricDisabled()
            return
        }
        val cipher = viewModel.biometricVault.encryptCipher(username)
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val authedCipher = result.cryptoObject?.cipher ?: return
                    viewModel.biometricVault.saveToken(username, authedCipher, refreshToken)
                    viewModel.onSavedToVault()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    viewModel.onBiometricDisabled()
                }
            }
        )
        prompt.authenticate(
            biometricPromptInfo("Lưu đăng nhập", "Xác thực vân tay để lần sau mở app không cần mật khẩu"),
            BiometricPrompt.CryptoObject(cipher)
        )
    }

    private fun promptUnlockVault(username: String) {
        val cipher = try {
            viewModel.biometricVault.decryptCipher(username)
        } catch (e: Exception) {
            if (e.isKeyInvalidated()) viewModel.onVaultInvalidated(username)
            return
        }
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val authedCipher = result.cryptoObject?.cipher ?: return
                    val refreshToken = viewModel.biometricVault.readToken(username, authedCipher)
                    viewModel.onBiometricUnlocked(username, refreshToken)
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

    private fun startBiometricEnrollmentFlow() {
        val username = viewModel.currentUsername() ?: return
        val biometricManager = BiometricManager.from(this)
        when (biometricManager.canAuthenticate(BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                viewModel.biometricVault.setBiometricEnabled(username, true)
                promptSaveToVault()
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                viewModel.onBiometricUnavailable(
                    "Thiết bị chưa đăng ký vân tay/khuôn mặt/PIN. Đang mở màn hình đăng ký…"
                )
                awaitingBiometricEnrollment = true
                val enrollIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
                        putExtra(
                            Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                            BIOMETRIC_STRONG
                        )
                    }
                } else {
                    Intent(Settings.ACTION_SECURITY_SETTINGS)
                }
                startActivity(enrollIntent)
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                viewModel.onBiometricUnavailable("Thiết bị không hỗ trợ xác thực sinh trắc học.")
            }
            else -> {
                viewModel.onBiometricUnavailable("Không thể bật đăng nhập bằng vân tay lúc này.")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (awaitingBiometricEnrollment) {
            awaitingBiometricEnrollment = false
            startBiometricEnrollmentFlow()
        }
    }

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
                        onUnlockClick = { username -> promptUnlockVault(username) },
                        onLogoutClick = {
                            val logoutIntent = viewModel.authManager.buildLogoutIntent(viewModel.idTokenForLogout())
                            viewModel.onLoggedOut()
                            logoutBrowserLauncher.launch(logoutIntent)
                        },
                        onBiometricToggle = { enable ->
                            if (enable) {
                                startBiometricEnrollmentFlow()
                            } else {
                                viewModel.onBiometricDisabled()
                            }
                        },
                        onScanQrClick = { mode -> viewModel.onScanQrClicked(mode) },
                        onQrDetected = { rawValue -> viewModel.onQrCodeScanned(rawValue) },
                        onQrScanCancel = { viewModel.onQrScanDismissed() },
                        onQrApproveResultDismiss = { viewModel.onQrApproveResultDismissed() },
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
    onUnlockClick: (String) -> Unit,
    onLogoutClick: () -> Unit,
    onBiometricToggle: (Boolean) -> Unit,
    onScanQrClick: (QrLoginMode) -> Unit,
    onQrDetected: (String) -> Unit,
    onQrScanCancel: () -> Unit,
    onQrApproveResultDismiss: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Bookstore — Biometric") }) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                is UiState.LoggedOut -> LoginScreen(
                    savedUsername = s.savedUsername,
                    onLoginClick = onLoginClick,
                    onUnlockClick = { username -> onUnlockClick(username) },
                )
                is UiState.LoggingIn -> LoadingScreen("Đang mở trang đăng nhập…")
                is UiState.LoggedIn -> if (s.scanningQrMode != null) {
                    QrScannerScreen(
                        onQrDetected = onQrDetected,
                        onCancel = onQrScanCancel,
                    )
                } else {
                    BooksScreen(
                        state = s,
                        onRefresh = { viewModel.loadBooks() },
                        onLogout = onLogoutClick,
                        onBiometricToggle = onBiometricToggle,
                        onScanQrClick = onScanQrClick,
                        onQrApproveResultDismiss = onQrApproveResultDismiss,
                    )
                }
            }
        }
    }
}

@Composable
private fun LoginScreen(
    savedUsername: String?,
    onLoginClick: () -> Unit,
    onUnlockClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Chào mừng đến Bookstore", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))

        if (savedUsername != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onLoginClick, modifier = Modifier.fillMaxWidth()) {
                Text("Đăng nhập bằng Keycloak")
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { onUnlockClick(savedUsername) }, modifier = Modifier.fillMaxWidth()) {
                Text("Đăng nhập bằng vân tay cho \"$savedUsername\"")
            }
        } else {
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
    onLogout: () -> Unit,
    onBiometricToggle: (Boolean) -> Unit,
    onScanQrClick: (QrLoginMode) -> Unit,
    onQrApproveResultDismiss: () -> Unit,
) {
    state.qrApproveResult?.let { result ->
        QrApproveResultDialog(result = result, onDismiss = onQrApproveResultDismiss)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Xin chào, ${state.username}", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Đăng nhập bằng vân tay", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = state.biometricEnabled, onCheckedChange = onBiometricToggle)
        }
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
            OutlinedButton(
                onClick = { onScanQrClick(QrLoginMode.LEGACY) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Quét QR trên trang web thường")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { onScanQrClick(QrLoginMode.KEYCLOAK_SPI) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Quét QR trên trang đăng nhập Keycloak")
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                Text("Đăng xuất")
            }
        }
    }
}

@Composable
private fun QrApproveResultDialog(
    result: QrApproveResult,
    onDismiss: () -> Unit,
) {
    when (result) {
        is QrApproveResult.Approving -> AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Đang xác nhận…") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp).fillMaxWidth(0.2f))
                    Spacer(Modifier.height(8.dp))
                    Text("Đang đăng nhập giúp thiết bị kia…")
                }
            },
        )
        is QrApproveResult.Success -> AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = { Button(onClick = onDismiss) { Text("OK") } },
            title = { Text("Thành công") },
            text = { Text("Đã đăng nhập giúp thiết bị kia. Kiểm tra lại trên trình duyệt.") },
        )
        is QrApproveResult.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = { Button(onClick = onDismiss) { Text("Đóng") } },
            title = { Text("Thất bại") },
            text = { Text(result.message) },
        )
    }
}

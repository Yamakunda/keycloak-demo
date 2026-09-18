import type { TokenSet } from '../auth/authManager';
import type { Book } from '../api/bookstoreApi';

export type QrLoginMode = 'LEGACY' | 'KEYCLOAK_SPI';

export type QrApproveResult =
  | { kind: 'approving' }
  | { kind: 'success' }
  | { kind: 'failed'; message: string };

// Chỉ phát sinh cho KEYCLOAK_SPI: đã gọi /qr-login/scan xong (danh tính đã ghi nhận) nhưng
// CHƯA gọi /qr-login/approve — chờ người dùng xác nhận bằng biometric trên chính điện
// thoại trước khi thật sự cấp quyền cho thiết bị kia.
export interface PendingQrConfirmation {
  apiUrl: string;
  sessionId: string;
}

export type UiState =
  | { kind: 'loggedOut'; savedUsername: string | null }
  | { kind: 'loggingIn' }
  | {
      kind: 'loggedIn';
      username: string;
      error: string | null;
      biometricEnabled: boolean;
      // Đang mở màn hình quét QR để đăng nhập chéo thiết bị — null nghĩa là không quét;
      // khác null cho biết đang quét cho nguồn QR nào để gọi đúng logic xử lý.
      scanningQrMode: QrLoginMode | null;
      // LEGACY: URL verification_uri_complete cần mở bằng trình duyệt sau khi quét.
      urlToOpen: string | null;
      qrApproveResult: QrApproveResult | null;
      pendingQrConfirmation: PendingQrConfirmation | null;
      books: Book[];
      loadingBooks: boolean;
    };

export type Action =
  | { type: 'loginStarted' }
  | { type: 'loginSucceeded'; username: string; biometricEnabled: boolean }
  | { type: 'loginFailed'; savedUsername: string | null }
  | { type: 'loggedOut'; savedUsername: string | null }
  | { type: 'biometricSaved' }
  | { type: 'biometricDisabled' }
  | { type: 'biometricUnavailable'; message: string }
  | { type: 'scanQrClicked'; mode: QrLoginMode }
  | { type: 'qrScanDismissed' }
  | { type: 'legacyQrScanned'; url: string }
  | { type: 'legacyQrInvalid' }
  | { type: 'urlOpened' }
  | { type: 'spiQrInvalid' }
  | { type: 'spiScanStarted' }
  | { type: 'spiScanSucceeded'; pending: PendingQrConfirmation }
  | { type: 'spiScanFailed'; message: string }
  | { type: 'qrApproveResultDismissed' }
  | { type: 'qrConfirmationDismissed' }
  | { type: 'qrApproveStarted' }
  | { type: 'qrApproveSucceeded' }
  | { type: 'qrApproveFailed'; message: string }
  | { type: 'booksLoadStarted' }
  | { type: 'booksLoadSucceeded'; books: Book[] }
  | { type: 'booksLoadFailed'; message: string };

export interface AuthSession {
  tokens: TokenSet;
}

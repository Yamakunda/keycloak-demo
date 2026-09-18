import type { Action, UiState } from './types';

export const initialState: UiState = { kind: 'loggedOut', savedUsername: null };

export function reducer(state: UiState, action: Action): UiState {
  switch (action.type) {
    case 'loginStarted':
      return { kind: 'loggingIn' };
    case 'loginSucceeded':
      return {
        kind: 'loggedIn',
        username: action.username,
        error: null,
        biometricEnabled: action.biometricEnabled,
        scanningQrMode: null,
        urlToOpen: null,
        qrApproveResult: null,
        pendingQrConfirmation: null,
        books: [],
        loadingBooks: false,
      };
    case 'loginFailed':
      return { kind: 'loggedOut', savedUsername: action.savedUsername };
    case 'loggedOut':
      return { kind: 'loggedOut', savedUsername: action.savedUsername };
    case 'biometricSaved':
      return state.kind === 'loggedIn' ? { ...state, biometricEnabled: true } : state;
    case 'biometricDisabled':
      return state.kind === 'loggedIn' ? { ...state, biometricEnabled: false } : state;
    case 'biometricUnavailable':
      return state.kind === 'loggedIn'
        ? { ...state, error: action.message, biometricEnabled: false }
        : state;
    case 'scanQrClicked':
      return state.kind === 'loggedIn' ? { ...state, scanningQrMode: action.mode } : state;
    case 'qrScanDismissed':
      return state.kind === 'loggedIn' ? { ...state, scanningQrMode: null } : state;
    case 'legacyQrScanned':
      return state.kind === 'loggedIn'
        ? { ...state, scanningQrMode: null, urlToOpen: action.url }
        : state;
    case 'legacyQrInvalid':
      return state.kind === 'loggedIn'
        ? { ...state, scanningQrMode: null, error: 'Mã QR không hợp lệ.' }
        : state;
    case 'urlOpened':
      return state.kind === 'loggedIn' ? { ...state, urlToOpen: null } : state;

    // KEYCLOAK_SPI: /qr-login/scan trước, chưa cấp quyền — chờ xác nhận biometric.
    case 'spiQrInvalid':
      return state.kind === 'loggedIn'
        ? { ...state, scanningQrMode: null, qrApproveResult: { kind: 'failed', message: 'Mã QR không hợp lệ.' } }
        : state;
    case 'spiScanStarted':
      return state.kind === 'loggedIn'
        ? { ...state, scanningQrMode: null, qrApproveResult: { kind: 'approving' } }
        : state;
    case 'spiScanSucceeded':
      return state.kind === 'loggedIn'
        ? { ...state, qrApproveResult: null, pendingQrConfirmation: action.pending }
        : state;
    case 'spiScanFailed':
      return state.kind === 'loggedIn'
        ? { ...state, qrApproveResult: { kind: 'failed', message: action.message } }
        : state;
    case 'qrApproveResultDismissed':
      return state.kind === 'loggedIn' ? { ...state, qrApproveResult: null } : state;
    case 'qrConfirmationDismissed':
      return state.kind === 'loggedIn' ? { ...state, pendingQrConfirmation: null } : state;
    case 'qrApproveStarted':
      return state.kind === 'loggedIn'
        ? { ...state, pendingQrConfirmation: null, qrApproveResult: { kind: 'approving' } }
        : state;
    case 'qrApproveSucceeded':
      return state.kind === 'loggedIn' ? { ...state, qrApproveResult: { kind: 'success' } } : state;
    case 'qrApproveFailed':
      return state.kind === 'loggedIn'
        ? { ...state, qrApproveResult: { kind: 'failed', message: action.message } }
        : state;

    case 'booksLoadStarted':
      return state.kind === 'loggedIn' ? { ...state, loadingBooks: true, error: null } : state;
    case 'booksLoadSucceeded':
      return state.kind === 'loggedIn' ? { ...state, books: action.books, loadingBooks: false } : state;
    case 'booksLoadFailed':
      return state.kind === 'loggedIn' ? { ...state, loadingBooks: false, error: action.message } : state;

    default:
      return state;
  }
}

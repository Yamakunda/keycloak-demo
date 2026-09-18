import * as WebBrowser from 'expo-web-browser';
import React, { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, Button, FlatList, StyleSheet, Switch, Text, View } from 'react-native';
import { logout } from '../auth/authManager';
import { checkBiometricEnrollment } from '../auth/biometricEnrollment';
import { promptConfirmQrApprove, promptSaveToVault } from '../auth/biometricPrompts';
import { fetchBooks, type Book } from '../api/bookstoreApi';
import * as qrLoginApi from '../qr/qrLoginApi';
import { QrScannerScreen } from '../qr/QrScannerScreen';
import { useApp } from '../state/AppContext';
import type { QrLoginMode } from '../state/types';
import { clear, setBiometricEnabled } from '../vault/biometricVault';
import { QrApproveResultDialog } from './QrApproveResultDialog';
import { QrConfirmDialog } from './QrConfirmDialog';

export function BooksScreen({ username, error }: { username: string; error: string | null }) {
  const { state, dispatch, tokensRef } = useApp();
  const biometricEnabled = state.kind === 'loggedIn' ? state.biometricEnabled : false;
  const scanningQrMode = state.kind === 'loggedIn' ? state.scanningQrMode : null;
  const urlToOpen = state.kind === 'loggedIn' ? state.urlToOpen : null;
  const qrApproveResult = state.kind === 'loggedIn' ? state.qrApproveResult : null;
  const pendingQrConfirmation = state.kind === 'loggedIn' ? state.pendingQrConfirmation : null;
  const books = state.kind === 'loggedIn' ? state.books : [];
  const loadingBooks = state.kind === 'loggedIn' ? state.loadingBooks : false;

  // Trên iOS, CameraView (native full-screen presentation) chưa dismiss animation xong
  // ngay khi scanningQrMode chuyển về null — Modal present ngay lập tức lúc đó bị UIKit
  // âm thầm từ chối ("Attempt to present ... while a presentation is in progress", chỉ
  // log native, không lên JS console) nên dialog QR không bao giờ hiện dù state đúng.
  // Trễ nhẹ để camera chắc chắn đã đóng hẳn trước khi cho phép Modal mount.
  const [dialogsReady, setDialogsReady] = useState(scanningQrMode === null);
  useEffect(() => {
    if (scanningQrMode !== null) {
      setDialogsReady(false);
      return;
    }
    const timer = setTimeout(() => setDialogsReady(true), 400);
    return () => clearTimeout(timer);
  }, [scanningQrMode]);

  const onRefresh = useCallback(async () => {
    const accessToken = tokensRef.current?.accessToken;
    if (!accessToken) return;
    dispatch({ type: 'booksLoadStarted' });
    const result = await fetchBooks(accessToken);
    if (result.ok) {
      dispatch({ type: 'booksLoadSucceeded', books: result.books });
    } else {
      dispatch({ type: 'booksLoadFailed', message: result.error });
    }
  }, [dispatch, tokensRef]);

  // Tải danh sách sách ngay khi vào màn hình (đăng nhập xong hoặc mở khoá vân tay xong) —
  // tương đương AppViewModel gọi loadBooks() trong onFirstLoginResult/onBiometricUnlocked.
  useEffect(() => {
    onRefresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // LEGACY (Device Authorization Grant): mở verification_uri_complete bằng trình duyệt hệ
  // thống ngay khi state set urlToOpen — plain browser tab (openBrowserAsync), KHÔNG phải
  // auth session, vì không cần bắt redirect nào ở đây. Trên Android chia sẻ cookie Chrome
  // giống Custom Tabs bản gốc; trên iOS không đảm bảo dùng chung phiên Safari.
  useEffect(() => {
    if (!urlToOpen) return;
    WebBrowser.openBrowserAsync(urlToOpen).finally(() => dispatch({ type: 'urlOpened' }));
  }, [urlToOpen, dispatch]);

  const onLogoutPress = async () => {
    // Logout does NOT clear the vault — same as AppViewModel.onLoggedOut/BiometricVault:
    // next login as this same user on this device can reuse the fingerprint immediately.
    const idToken = tokensRef.current?.idToken ?? null;
    tokensRef.current = null;
    dispatch({ type: 'loggedOut', savedUsername: biometricEnabled ? username : null });
    await logout(idToken);
  };

  const onBiometricToggle = async (enable: boolean) => {
    if (!enable) {
      await clear(username);
      await setBiometricEnabled(username, false);
      dispatch({ type: 'biometricDisabled' });
      return;
    }

    const enrollment = await checkBiometricEnrollment();
    if (!enrollment.ok) {
      dispatch({ type: 'biometricUnavailable', message: enrollment.message });
      return;
    }

    const refreshToken = tokensRef.current?.refreshToken;
    if (!refreshToken) {
      dispatch({ type: 'biometricUnavailable', message: 'Phiên đăng nhập đã hết hạn, hãy đăng nhập lại.' });
      return;
    }

    try {
      await promptSaveToVault(username, refreshToken);
      await setBiometricEnabled(username, true);
      dispatch({ type: 'biometricSaved' });
    } catch {
      dispatch({ type: 'biometricDisabled' });
    }
  };

  const onScanQrClick = (mode: QrLoginMode) => {
    // Chặn mở lại camera khi đang có dialog QR khác chờ xử lý — nếu không, scanningQrMode
    // chuyển khác null khiến BooksScreen return sớm ở nhánh camera (dòng dưới), làm biến
    // mất toàn bộ dialog (qrApproveResult/pendingQrConfirmation) khỏi cây render dù state
    // vẫn còn nguyên — trông như app "đứng màn hình".
    if (pendingQrConfirmation || qrApproveResult) return;
    dispatch({ type: 'scanQrClicked', mode });
  };

  const onQrDetected = async (rawValue: string) => {
    // Camera native (AVCaptureSession trên iOS) có độ trễ khi dừng sau khi unmount —
    // onBarcodeScanned có thể bắn thêm 1-2 lần với cùng QR trước khi CameraView thực sự
    // biến mất khỏi cây render. Chặn ở đây bằng state (không chỉ ref cục bộ trong
    // QrScannerScreen) để không tạo đè pendingQrConfirmation/qrApproveResult mới lên
    // dialog đang chờ xử lý — nếu không dialog sẽ liên tục bị reset trông như "biến mất".
    if (pendingQrConfirmation || qrApproveResult) return;

    if (scanningQrMode === 'LEGACY') {
      if (!rawValue.startsWith('http://') && !rawValue.startsWith('https://')) {
        dispatch({ type: 'legacyQrInvalid' });
        return;
      }
      dispatch({ type: 'legacyQrScanned', url: rawValue });
      return;
    }

    // KEYCLOAK_SPI: gọi /scan trước, chưa cấp quyền — chờ xác nhận biometric.
    const accessToken = tokensRef.current?.accessToken;
    if (!accessToken) {
      dispatch({ type: 'spiScanFailed', message: 'Phiên đăng nhập đã hết hạn, hãy đăng nhập lại.' });
      return;
    }

    const parsed = qrLoginApi.parseQrPayload(rawValue);
    if (!parsed) {
      dispatch({ type: 'spiQrInvalid' });
      return;
    }

    dispatch({ type: 'spiScanStarted' });
    const result = await qrLoginApi.scan(parsed.apiUrl, parsed.sessionId, accessToken);
    if (result.ok) {
      dispatch({ type: 'spiScanSucceeded', pending: parsed });
    } else {
      dispatch({ type: 'spiScanFailed', message: result.error });
    }
  };

  const onQrConfirmClick = async () => {
    if (!pendingQrConfirmation) return;
    const confirmed = await promptConfirmQrApprove();
    if (!confirmed) {
      await onQrConfirmDismiss();
      return;
    }

    const accessToken = tokensRef.current?.accessToken;
    if (!accessToken) {
      dispatch({ type: 'qrApproveFailed', message: 'Phiên đăng nhập đã hết hạn, hãy đăng nhập lại.' });
      return;
    }

    dispatch({ type: 'qrApproveStarted' });
    const result = await qrLoginApi.approve(pendingQrConfirmation.apiUrl, pendingQrConfirmation.sessionId, accessToken);
    if (result.ok) {
      dispatch({ type: 'qrApproveSucceeded' });
    } else {
      dispatch({ type: 'qrApproveFailed', message: result.error });
    }
  };

  // Người dùng bấm "Từ chối" hoặc biometric thất bại — giải phóng session QR phía kia.
  const onQrConfirmDismiss = async () => {
    if (!pendingQrConfirmation) return;
    const pending = pendingQrConfirmation;
    dispatch({ type: 'qrConfirmationDismissed' });
    const accessToken = tokensRef.current?.accessToken;
    if (!accessToken) return;
    await qrLoginApi.cancel(pending.apiUrl, pending.sessionId, accessToken);
  };

  if (scanningQrMode !== null) {
    return (
      <QrScannerScreen
        onQrDetected={onQrDetected}
        onCancel={() => dispatch({ type: 'qrScanDismissed' })}
      />
    );
  }

  return (
    <View style={styles.container}>
      {dialogsReady && qrApproveResult && (
        <QrApproveResultDialog
          result={qrApproveResult}
          onDismiss={() => dispatch({ type: 'qrApproveResultDismissed' })}
        />
      )}
      {dialogsReady && pendingQrConfirmation && (
        <QrConfirmDialog onConfirm={onQrConfirmClick} onDismiss={onQrConfirmDismiss} />
      )}

      <View style={styles.header}>
        <Text style={styles.title}>Xin chào, {username}</Text>
        <View style={styles.spacer} />

        <View style={styles.row}>
          <Text>Đăng nhập bằng vân tay</Text>
          <Switch value={biometricEnabled} onValueChange={onBiometricToggle} />
        </View>

        {error ? <Text style={styles.error}>Lỗi: {error}</Text> : null}
      </View>

      {loadingBooks ? (
        <View style={styles.loading}>
          <ActivityIndicator size="large" />
          <Text style={styles.loadingText}>Đang tải danh sách sách…</Text>
        </View>
      ) : (
        <FlatList
          style={styles.list}
          data={books}
          keyExtractor={(item) => String(item.id)}
          renderItem={({ item }: { item: Book }) => (
            <View style={styles.bookRow}>
              <Text style={styles.bookTitle}>{item.title}</Text>
              <Text style={styles.bookMeta}>
                {item.author} · {item.genre ?? '—'}
              </Text>
              <Text style={styles.bookMeta}>{Math.trunc(item.price)} đ</Text>
            </View>
          )}
          ItemSeparatorComponent={() => <View style={styles.divider} />}
        />
      )}

      <View style={styles.footer}>
        <Button title="Làm mới" onPress={onRefresh} />
        <View style={styles.smallSpacer} />
        <Button title="Quét QR trên trang web thường (Device Grant)" onPress={() => onScanQrClick('LEGACY')} />
        <View style={styles.smallSpacer} />
        <Button title="Quét QR trên trang đăng nhập Keycloak" onPress={() => onScanQrClick('KEYCLOAK_SPI')} />
        <View style={styles.smallSpacer} />
        <Button title="Đăng xuất" onPress={onLogoutPress} />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: 16 },
  header: { marginBottom: 8 },
  title: { fontSize: 18, fontWeight: '600' },
  spacer: { height: 8 },
  smallSpacer: { height: 8 },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  error: { color: 'red', marginTop: 8 },
  loading: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  loadingText: { marginTop: 16 },
  list: { flex: 1 },
  bookRow: { paddingVertical: 8 },
  bookTitle: { fontSize: 15, fontWeight: '600' },
  bookMeta: { fontSize: 13, color: '#555' },
  divider: { height: 1, backgroundColor: '#e0e0e0' },
  footer: { paddingTop: 12 },
});

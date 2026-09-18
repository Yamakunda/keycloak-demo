import * as AuthSession from 'expo-auth-session';
import React, { useState } from 'react';
import { Button, StyleSheet, Text, View } from 'react-native';
import { buildAuthRequestConfig, discovery, exchangeCode } from '../auth/authManager';
import { checkBiometricEnrollment } from '../auth/biometricEnrollment';
import { promptSaveToVault, promptUnlockVault } from '../auth/biometricPrompts';
import { decodePreferredUsername } from '../auth/jwt';
import { useApp } from '../state/AppContext';
import { isBiometricEnabled } from '../vault/biometricVault';

export function LoginScreen({ savedUsername }: { savedUsername: string | null }) {
  const { dispatch, tokensRef } = useApp();
  const [request, , promptAsync] = AuthSession.useAuthRequest(buildAuthRequestConfig(), discovery);
  const [unlockError, setUnlockError] = useState<string | null>(null);

  const onLoginPress = async () => {
    if (!request) return;
    dispatch({ type: 'loginStarted' });

    const result = await promptAsync();
    if (result.type !== 'success') {
      dispatch({ type: 'loginFailed', savedUsername });
      return;
    }

    try {
      const tokens = await exchangeCode(result.params.code, request.codeVerifier);
      const username = tokens.idToken ? decodePreferredUsername(tokens.idToken) : null;
      const resolvedUsername = username ?? 'user';
      tokensRef.current = tokens;

      const biometricEnabled = await isBiometricEnabled(resolvedUsername);
      dispatch({ type: 'loginSucceeded', username: resolvedUsername, biometricEnabled });

      // Analog of MainActivity's post-login startBiometricEnrollmentFlow() call: if this
      // account previously had biometric enabled, silently refresh the saved vault entry
      // with the new refresh_token rather than requiring the user to re-toggle it.
      if (biometricEnabled && tokens.refreshToken) {
        const enrollment = await checkBiometricEnrollment();
        if (enrollment.ok) {
          await promptSaveToVault(resolvedUsername, tokens.refreshToken);
        } else {
          dispatch({ type: 'biometricUnavailable', message: enrollment.message });
        }
      }
    } catch {
      dispatch({ type: 'loginFailed', savedUsername });
    }
  };

  const onUnlockPress = async () => {
    if (!savedUsername) return;
    setUnlockError(null);
    dispatch({ type: 'loginStarted' });
    try {
      const tokens = await promptUnlockVault(savedUsername);
      tokensRef.current = tokens;
      dispatch({ type: 'loginSucceeded', username: savedUsername, biometricEnabled: true });
    } catch {
      setUnlockError('Không thể mở khoá bằng vân tay. Hãy đăng nhập lại bằng mật khẩu.');
      dispatch({ type: 'loginFailed', savedUsername: null });
    }
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Chào mừng đến Bookstore</Text>

      {savedUsername ? (
        <>
          <View style={styles.spacer} />
          <Button title="Đăng nhập bằng Keycloak" onPress={onLoginPress} disabled={!request} />
          <View style={styles.smallSpacer} />
          <Button title={`Đăng nhập bằng vân tay cho "${savedUsername}"`} onPress={onUnlockPress} />
          {unlockError ? <Text style={styles.error}>{unlockError}</Text> : null}
        </>
      ) : (
        <>
          <Text style={styles.subtitle}>
            Đăng nhập bằng mật khẩu 1 lần — lần sau mở app chỉ cần vân tay
          </Text>
          <View style={styles.spacer} />
          <Button title="Đăng nhập" onPress={onLoginPress} disabled={!request} />
        </>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24 },
  title: { fontSize: 20, fontWeight: '600', marginBottom: 8, textAlign: 'center' },
  subtitle: { textAlign: 'center', color: '#555' },
  spacer: { height: 24 },
  smallSpacer: { height: 12 },
  error: { color: 'red', marginTop: 12, textAlign: 'center' },
});

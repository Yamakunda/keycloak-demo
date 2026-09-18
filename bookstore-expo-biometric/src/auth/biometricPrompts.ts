import * as LocalAuthentication from 'expo-local-authentication';
import { exchangeRefreshToken, type TokenSet } from './authManager';
import { clear, readToken, saveToken } from '../vault/biometricVault';

/**
 * Called right after a fresh login when the user opts in to biometric unlock — analog of
 * MainActivity.promptSaveToVault + BiometricVault.saveToken. On Android the OS biometric
 * prompt fires as part of the underlying Keychain write itself (see biometricVault.ts).
 */
export async function promptSaveToVault(username: string, refreshToken: string): Promise<void> {
  await saveToken(username, refreshToken);
}

/**
 * Called from the "unlock with fingerprint" login-screen button — analog of
 * MainActivity.promptUnlockVault + AppViewModel.onBiometricUnlocked. Any failure (wrong
 * biometric, cancelled, or the item became unreadable because enrolled biometrics changed)
 * is treated as vault invalidation: the caller should clear this account's vault and force
 * a fresh password login, since — unlike Android's KeyPermanentlyInvalidatedException —
 * there's no cross-platform way to distinguish "wrong finger, try again" from "key is
 * permanently gone" here.
 */
export async function promptUnlockVault(username: string): Promise<TokenSet> {
  let refreshToken: string;
  try {
    refreshToken = await readToken(username, 'Mở khoá Bookstore', 'Xác thực vân tay để đăng nhập');
  } catch (e) {
    await clear(username);
    throw e;
  }

  try {
    return await exchangeRefreshToken(refreshToken);
  } catch (e) {
    await clear(username);
    throw e;
  }
}

/**
 * Xác nhận "cho phép thiết bị kia đăng nhập" sau khi đã quét QR KEYCLOAK_SPI — chỉ xác
 * thực danh tính ngay trên điện thoại (presence-only, KHÔNG đụng vault/CryptoObject vì
 * không giải mã dữ liệu nào ở đây, khác hẳn promptSaveToVault/promptUnlockVault). Thất
 * bại/huỷ => coi như từ chối. Tương đương MainActivity.promptConfirmQrApprove.
 */
export async function promptConfirmQrApprove(): Promise<boolean> {
  const result = await LocalAuthentication.authenticateAsync({
    promptMessage: 'Xác nhận đăng nhập',
    promptSubtitle: 'Xác thực vân tay để cho phép thiết bị kia đăng nhập',
    cancelLabel: 'Huỷ',
  });
  return result.success;
}

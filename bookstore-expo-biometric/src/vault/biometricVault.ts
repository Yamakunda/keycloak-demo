import AsyncStorageFallback from '@react-native-async-storage/async-storage';
import * as Keychain from 'react-native-keychain';

/**
 * Per-account refresh_token vault gated by biometrics — RN analog of BiometricVault.kt.
 *
 * Android: `storage: AES_GCM` + `accessControl: BIOMETRY_CURRENT_SET` + `securityLevel:
 * SECURE_HARDWARE` maps to the same primitive as the Kotlin code (Keystore AES key with
 * setUserAuthenticationRequired(true)) — the OS itself refuses to run the decrypt unless a
 * fresh biometric auth just happened.
 * iOS: the same accessControl maps to a Secure Enclave-backed Keychain ACL — equivalent
 * guarantee ("no biometric, no secret"), but getGenericPassword() returns the secret
 * directly once Face ID/Touch ID passes, there is no exposed partially-authenticated
 * Cipher/CryptoObject the way Android's BiometricPrompt.CryptoObject works.
 *
 * Each account gets its own Keychain "service" key (namespaced by username), matching the
 * Kotlin vault's per-username SharedPreferences/Keystore-alias namespacing so multiple
 * accounts can have biometric enabled on one device without collision, and logout does NOT
 * clear the vault (only explicit disable or an invalidated read does).
 */

const SERVICE_PREFIX = 'bookstore_biometric_vault_';
const LAST_USERNAME_KEY = 'bookstore_biometric_vault_last_username';

function serviceFor(username: string): string {
  return `${SERVICE_PREFIX}${username}`;
}

export async function lastUsername(): Promise<string | null> {
  try {
    return await AsyncStorageFallback.getItem(LAST_USERNAME_KEY);
  } catch {
    return null;
  }
}

async function setLastUsername(username: string): Promise<void> {
  await AsyncStorageFallback.setItem(LAST_USERNAME_KEY, username);
}

export async function hasStoredToken(username: string): Promise<boolean> {
  return Keychain.hasGenericPassword({ service: serviceFor(username) });
}

/**
 * Saves the refresh_token under a biometry-gated Keychain entry. The OS biometric prompt
 * fires as part of THIS call on read, not on write — matching the source app's split
 * between "encrypt (no prompt needed to encrypt on iOS/Android's AES_GCM write path,
 * only reads require re-auth)" is not quite true for Android (AES_GCM requires auth for
 * BOTH directions per the enum's own doc) — so on Android this save call itself may
 * trigger a biometric prompt, unlike Kotlin's encryptCipher() which the caller has
 * already unlocked via a preceding BiometricPrompt. Call this immediately after a
 * successful login so the OS prompt reads as "confirm saving login" to the user.
 */
export async function saveToken(username: string, refreshToken: string): Promise<void> {
  const result = await Keychain.setGenericPassword(username, refreshToken, {
    service: serviceFor(username),
    accessControl: Keychain.ACCESS_CONTROL.BIOMETRY_CURRENT_SET,
    securityLevel: Keychain.SECURITY_LEVEL.SECURE_HARDWARE,
    storage: Keychain.STORAGE_TYPE.AES_GCM,
    accessible: Keychain.ACCESSIBLE.WHEN_UNLOCKED_THIS_DEVICE_ONLY,
    authenticationPrompt: { title: 'Lưu đăng nhập' },
  });
  if (!result) throw new Error('Không thể lưu vào vault sinh trắc học.');
  await setLastUsername(username);
}

/**
 * Reads the refresh_token, triggering the OS biometric prompt as part of the Keychain
 * read itself. Throws on any failure (wrong biometric, cancelled, or the item became
 * unreadable because enrolled biometrics changed) — callers should treat ANY throw here
 * as "vault invalidated, force password re-login", matching the isKeyInvalidated()
 * fallback recommended in the port plan since iOS has no distinguishable
 * KeyPermanentlyInvalidatedException-equivalent error type to pattern-match on.
 */
export async function readToken(username: string, promptTitle: string, promptSubtitle: string): Promise<string> {
  const result = await Keychain.getGenericPassword({
    service: serviceFor(username),
    authenticationPrompt: { title: promptTitle, subtitle: promptSubtitle },
  });
  if (!result) throw new Error('Không thể mở khoá vault sinh trắc học.');
  return result.password;
}

/** Clears ONLY this account's vault entry — other accounts' vaults on this device are untouched. */
export async function clear(username: string): Promise<void> {
  await Keychain.resetGenericPassword({ service: serviceFor(username) });
}

export async function isBiometricEnabled(username: string): Promise<boolean> {
  try {
    return await AsyncStorageFallback.getItem(`${SERVICE_PREFIX}enabled_${username}`).then((v) => v === 'true');
  } catch {
    return false;
  }
}

export async function setBiometricEnabled(username: string, enabled: boolean): Promise<void> {
  await AsyncStorageFallback.setItem(`${SERVICE_PREFIX}enabled_${username}`, enabled ? 'true' : 'false');
}

export async function getSupportedBiometryType(): Promise<Keychain.BIOMETRY_TYPE | null> {
  return Keychain.getSupportedBiometryType();
}

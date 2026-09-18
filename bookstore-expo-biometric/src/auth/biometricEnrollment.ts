import * as LocalAuthentication from 'expo-local-authentication';

export type EnrollmentCheck =
  | { ok: true }
  | { ok: false; reason: 'not-enrolled' | 'no-hardware' | 'unavailable'; message: string };

/**
 * Analog of MainActivity.startBiometricEnrollmentFlow's BiometricManager.canAuthenticate
 * branch — checked before attempting to save to the vault. Unlike Android, there's no
 * direct "open enrollment settings" intent cross-platform; the caller should just show
 * the message and let the user go enroll via OS Settings themselves.
 */
export async function checkBiometricEnrollment(): Promise<EnrollmentCheck> {
  const hasHardware = await LocalAuthentication.hasHardwareAsync();
  if (!hasHardware) {
    return { ok: false, reason: 'no-hardware', message: 'Thiết bị không hỗ trợ xác thực sinh trắc học.' };
  }
  const isEnrolled = await LocalAuthentication.isEnrolledAsync();
  if (!isEnrolled) {
    return {
      ok: false,
      reason: 'not-enrolled',
      message: 'Thiết bị chưa đăng ký vân tay/khuôn mặt. Hãy đăng ký trong Cài đặt rồi thử lại.',
    };
  }
  return { ok: true };
}

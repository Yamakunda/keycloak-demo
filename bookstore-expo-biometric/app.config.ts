import type { ExpoConfig } from 'expo/config';

// Mirrors app/build.gradle.kts buildConfigField defaults in bookstore-android-biometric —
// override via EXPO_PUBLIC_* env vars (e.g. in .env or an EAS build profile) instead of
// editing this file, same role as Gradle's `project.findProperty("kcBaseUrl") ?: default`.
const keycloakBaseUrl = process.env.EXPO_PUBLIC_KEYCLOAK_BASE_URL ?? 'http://192.168.0.233:8080';
const keycloakRealm = process.env.EXPO_PUBLIC_KEYCLOAK_REALM ?? 'test';
const keycloakClientId = process.env.EXPO_PUBLIC_KEYCLOAK_CLIENT_ID ?? 'biometric-demo';
const bookstoreApiBaseUrl = process.env.EXPO_PUBLIC_BOOKSTORE_API_BASE_URL ?? 'http://192.168.0.233:3043';

const config: ExpoConfig = {
  name: 'bookstore-expo-biometric',
  slug: 'bookstore-expo-biometric',
  version: '1.0.0',
  orientation: 'portrait',
  icon: './assets/icon.png',
  userInterfaceStyle: 'light',
  scheme: 'bookstorebioexpo',
  ios: {
    supportsTablet: true,
    bundleIdentifier: 'com.snp.bookstorebioexpo',
    infoPlist: {
      // Keycloak/API endpoints point at a LAN IP over plain HTTP for this demo (matches
      // android:usesCleartextTraffic below) — iOS has no equivalent opt-in flag, ATS blocks
      // cleartext by default, so the exception must be declared explicitly here.
      NSAppTransportSecurity: {
        NSAllowsArbitraryLoads: true,
      },
      // Required by iOS whenever Face ID is used (expo-local-authentication / Keychain
      // biometric-gated reads) — without it, iOS silently falls back to passcode-only.
      NSFaceIDUsageDescription: 'Dùng Face ID để mở khoá đăng nhập nhanh và xác nhận đăng nhập QR.',
    },
  },
  android: {
    package: 'com.snp.bookstorebioexpo',
    adaptiveIcon: {
      backgroundColor: '#E6F4FE',
      foregroundImage: './assets/android-icon-foreground.png',
      backgroundImage: './assets/android-icon-background.png',
      monochromeImage: './assets/android-icon-monochrome.png',
    },
    predictiveBackGestureEnabled: false,
  },
  web: {
    favicon: './assets/favicon.png',
  },
  plugins: [
    'expo-web-browser',
    'expo-local-authentication',
    [
      'expo-camera',
      {
        cameraPermission: 'Cần quyền camera để quét mã QR đăng nhập chéo thiết bị.',
        // App chỉ quét QR (ảnh tĩnh), không quay video — bỏ quyền micro mặc định của plugin
        // (recordAudioAndroid tắt RECORD_AUDIO trên Android, microphonePermission: false bỏ
        // NSMicrophoneUsageDescription khỏi Info.plist trên iOS).
        microphonePermission: false,
        recordAudioAndroid: false,
      },
    ],
    [
      'expo-build-properties',
      {
        // Keycloak/API endpoints point at a LAN IP over plain HTTP for this demo — matches
        // AndroidManifest.xml's usesCleartextTraffic="true" in bookstore-android-biometric.
        android: { usesCleartextTraffic: true },
      },
    ],
  ],
  extra: {
    keycloakBaseUrl,
    keycloakRealm,
    keycloakClientId,
    bookstoreApiBaseUrl,
  },
};

export default config;

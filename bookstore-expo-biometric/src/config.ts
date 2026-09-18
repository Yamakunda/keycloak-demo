import Constants from 'expo-constants';

interface AppExtra {
  keycloakBaseUrl: string;
  keycloakRealm: string;
  keycloakClientId: string;
  bookstoreApiBaseUrl: string;
}

const extra = Constants.expoConfig?.extra as AppExtra | undefined;

if (!extra) {
  throw new Error('Missing app.config.ts "extra" block — check expo config loaded correctly.');
}

export const config = extra;

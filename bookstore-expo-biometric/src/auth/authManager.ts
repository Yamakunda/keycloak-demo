import * as AuthSession from 'expo-auth-session';
import * as WebBrowser from 'expo-web-browser';
import { config } from '../config';

WebBrowser.maybeCompleteAuthSession();

// offline_access -> Keycloak issues a long-lived refresh_token (client's offline session
// timeout, see test-realm.json client "biometric-demo") independent of the short-lived
// regular SSO session, mirroring AuthManager.kt's login scope.
const SCOPES = ['openid', 'profile', 'email', 'offline_access'];

const issuer = `${config.keycloakBaseUrl}/realms/${config.keycloakRealm}`;

// Built manually (not fetched via discovery doc) to mirror AuthManager.kt's
// AuthorizationServiceConfiguration, which also hardcodes these three endpoints.
export const discovery: AuthSession.DiscoveryDocument = {
  authorizationEndpoint: `${issuer}/protocol/openid-connect/auth`,
  tokenEndpoint: `${issuer}/protocol/openid-connect/token`,
  revocationEndpoint: `${issuer}/protocol/openid-connect/revoke`,
  endSessionEndpoint: `${issuer}/protocol/openid-connect/logout`,
};

export const redirectUri = AuthSession.makeRedirectUri({ scheme: 'bookstorebioexpo', path: 'oauth2redirect' });

export interface TokenSet {
  accessToken: string;
  refreshToken: string | null;
  idToken: string | null;
}

function toTokenSet(response: AuthSession.TokenResponse): TokenSet {
  return {
    accessToken: response.accessToken,
    refreshToken: response.refreshToken ?? null,
    idToken: response.idToken ?? null,
  };
}

export function buildAuthRequestConfig(): AuthSession.AuthRequestConfig {
  return {
    clientId: config.keycloakClientId,
    redirectUri,
    scopes: SCOPES,
    usePKCE: true,
    responseType: AuthSession.ResponseType.Code,
  };
}

/** First login only — opens the system browser for the Authorization Code + PKCE flow. */
export async function promptLogin(
  request: AuthSession.AuthRequest,
): Promise<AuthSession.AuthSessionResult> {
  return request.promptAsync(discovery);
}

/** Exchanges the authorization code from promptLogin's result for tokens. */
export async function exchangeCode(
  code: string,
  codeVerifier: string | undefined,
): Promise<TokenSet> {
  const response = await AuthSession.exchangeCodeAsync(
    {
      clientId: config.keycloakClientId,
      code,
      redirectUri,
      extraParams: codeVerifier ? { code_verifier: codeVerifier } : undefined,
    },
    discovery,
  );
  return toTokenSet(response);
}

/**
 * Uses a refresh_token (decrypted from BiometricVault-equivalent storage via biometric
 * unlock) to get a fresh access_token — no browser involved. Direct analog of
 * AuthManager.kt's exchangeRefreshToken.
 */
export async function exchangeRefreshToken(refreshToken: string): Promise<TokenSet> {
  const response = await AuthSession.refreshAsync(
    {
      clientId: config.keycloakClientId,
      refreshToken,
      scopes: SCOPES,
    },
    discovery,
  );
  return toTokenSet(response);
}

export async function logout(idToken: string | null): Promise<void> {
  const params = new URLSearchParams({ post_logout_redirect_uri: redirectUri });
  if (idToken) params.set('id_token_hint', idToken);
  const logoutUrl = `${discovery.endSessionEndpoint}?${params.toString()}`;
  await WebBrowser.openBrowserAsync(logoutUrl);
}

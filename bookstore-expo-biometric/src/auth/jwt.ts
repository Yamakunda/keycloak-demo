import { jwtDecode } from 'jwt-decode';

interface IdTokenClaims {
  preferred_username?: string;
}

export function decodePreferredUsername(idToken: string): string | null {
  try {
    const claims = jwtDecode<IdTokenClaims>(idToken);
    return claims.preferred_username ?? null;
  } catch {
    return null;
  }
}

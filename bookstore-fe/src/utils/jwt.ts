// Decode JWT payload để hiển thị (không verify — token nhận trực tiếp từ Keycloak /token)

export interface JwtClaims {
  sub?: string;
  name?: string;
  preferred_username?: string;
  given_name?: string;
  family_name?: string;
  email?: string;
  exp?: number;
  iat?: number;
  iss?: string;
  roles?: string[];
  [claim: string]: unknown;
}

export function decodeJwtPayload(token: string): JwtClaims | null {
  try {
    const payload = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    return JSON.parse(atob(payload)) as JwtClaims;
  } catch {
    return null;
  }
}

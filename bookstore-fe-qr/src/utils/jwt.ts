export function decodePreferredUsername(accessToken: string): string | undefined {
  try {
    const payload = accessToken.split(".")[1];
    const json = atob(payload.replace(/-/g, "+").replace(/_/g, "/"));
    return JSON.parse(json).preferred_username;
  } catch {
    return undefined;
  }
}

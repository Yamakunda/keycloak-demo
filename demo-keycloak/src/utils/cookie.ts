// Cookie helpers — access token lưu ở cookie để demo
// (xem được ở DevTools → Application → Cookies).

export function setCookie(name: string, value: string, maxAgeSec: number): void {
  document.cookie = `${name}=${encodeURIComponent(value)}; path=/; max-age=${maxAgeSec}; SameSite=Lax`;
}

export function getCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp(`(?:^|;\\s*)${name}=([^;]+)`));
  return match ? decodeURIComponent(match[1]) : null;
}

export function deleteCookie(name: string): void {
  document.cookie = `${name}=; path=/; max-age=0`;
}

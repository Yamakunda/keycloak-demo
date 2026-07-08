// Sinh chuỗi ngẫu nhiên (Web Crypto) — dùng cho tham số state chống CSRF

export function randomString(length = 32): string {
  const bytes = crypto.getRandomValues(new Uint8Array(length));
  return btoa(String.fromCharCode(...Array.from(bytes)))
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "")
    .slice(0, length);
}

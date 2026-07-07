// Đọc biến môi trường bắt buộc (CRA nhúng REACT_APP_* vào bundle lúc build).
export function required(name: string): string {
  const value = process.env[name];
  if (!value) {
    throw new Error(`Missing env var ${name} — copy .env.example thành .env rồi restart dev server`);
  }
  return value;
}

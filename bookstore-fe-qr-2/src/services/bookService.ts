import { API_URL } from "../config/api";

export interface Book {
  id: number;
  title: string;
  author: string;
  genre?: string;
  price: number;
  cover?: string;
}

export interface BooksResponse {
  total: number;
  authenticatedAs: string;
  books: Book[];
}

export class ApiError extends Error {
  constructor(message: string, public status: number) {
    super(message);
  }
}

// Gọi qua bookstore-api-qr-2 (proxy — không phải bookstore-api-mobile trực tiếp): backend
// đọc access_token từ cookie httpOnly rồi tự gắn Bearer header khi gọi hộ FE, vì FE không
// còn giữ token nào để tự gửi.
export async function getBooks(): Promise<BooksResponse> {
  const res = await fetch(`${API_URL}/api/books`, {
    credentials: "include",
  });
  if (!res.ok) {
    const data = await res.json().catch(() => ({}));
    throw new ApiError(data.message || "Không tải được danh sách sách", res.status);
  }
  return res.json();
}

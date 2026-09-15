import { BOOKS_API_URL } from "../config/api";

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

export async function getBooks(accessToken: string): Promise<BooksResponse> {
  const res = await fetch(`${BOOKS_API_URL}/api/books`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  if (!res.ok) {
    const data = await res.json().catch(() => ({}));
    throw new ApiError(data.message || "Không tải được danh sách sách", res.status);
  }
  return res.json();
}

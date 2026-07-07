// Book service — gọi bookstore-api, đính kèm access token từ cookie
// qua header "Authorization: Bearer ...". API verify token bằng Keycloak introspection.

import { API_URL } from "../config/api";
import { getCookie } from "../utils/cookie";

export interface Book {
  id: number;
  cover: string;
  title: string;
  author: string;
  price: number;
  genre: string;
}

export interface BookInput {
  title: string;
  author: string;
  price: number;
  genre?: string;
  cover?: string;
}

export interface BooksResponse {
  total: number;
  authenticatedAs: string;
  books: Book[];
}

export class ApiError extends Error {
  status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = getCookie("access_token");
  if (!token) {
    throw new ApiError(401, "Cookie access_token đã hết hạn — hãy đăng nhập lại");
  }

  const res = await fetch(`${API_URL}${path}`, {
    ...options,
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
      ...options.headers,
    },
  });

  const body = await res.json().catch(() => ({}));
  if (!res.ok) {
    const message =
      (Array.isArray(body.messages) && body.messages.join(", ")) ||
      body.message ||
      `API lỗi (${res.status})`;
    throw new ApiError(res.status, message);
  }
  return body as T;
}

export const getBooks = () => request<BooksResponse>("/api/books");

export const createBook = (input: BookInput) =>
  request<Book>("/api/books", { method: "POST", body: JSON.stringify(input) });

export const updateBook = (id: number, input: Partial<BookInput>) =>
  request<Book>(`/api/books/${id}`, { method: "PUT", body: JSON.stringify(input) });

export const deleteBook = (id: number) =>
  request<void>(`/api/books/${id}`, { method: "DELETE" });

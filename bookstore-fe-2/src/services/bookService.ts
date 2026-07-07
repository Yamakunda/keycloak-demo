// Book service — gọi bookstore-api; access_token nằm trong cookie httpOnly nên
// browser tự gửi kèm request (credentials: "include"). API verify token bằng Keycloak introspection.

import { API_URL } from "../config/api";

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
  const res = await fetch(`${API_URL}${path}`, {
    ...options,
    credentials: "include",
    headers: {
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

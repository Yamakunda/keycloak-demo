import { config } from '../config';

/**
 * Tương đương BookstoreApi.kt + BookRepositoryImpl.kt + GetBooksUseCase.kt + Book.kt/BookDto.kt
 * gộp lại — không cần tách layer riêng vì không có DI framework nào ép buộc điều đó.
 */
export interface Book {
  id: number;
  title: string;
  author: string;
  genre: string | null;
  price: number;
  cover: string | null;
}

interface BooksResponse {
  total: number;
  authenticatedAs: string | null;
  books: Book[];
}

export type FetchBooksResult = { ok: true; books: Book[] } | { ok: false; error: string };

export async function fetchBooks(accessToken: string): Promise<FetchBooksResult> {
  try {
    const response = await fetch(`${config.bookstoreApiBaseUrl}/api/books`, {
      headers: { Authorization: `Bearer ${accessToken}` },
    });

    if (!response.ok) {
      const body = await response.text().catch(() => '');
      return { ok: false, error: `HTTP ${response.status}: ${body}` };
    }

    const data: BooksResponse = await response.json();
    return { ok: true, books: data.books };
  } catch (e) {
    return { ok: false, error: e instanceof Error ? e.message : 'Lỗi không xác định' };
  }
}

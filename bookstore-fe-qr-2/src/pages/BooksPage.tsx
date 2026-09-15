import { useEffect, useState } from "react";
import { Book, getBooks } from "../services/bookService";
import { logout, TokenResponse } from "../services/authService";

interface Props {
  token: TokenResponse;
  username?: string;
}

export default function BooksPage({ token, username }: Props) {
  const [books, setBooks] = useState<Book[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError("");
    getBooks(token.access_token)
      .then((data) => {
        if (cancelled) return;
        setBooks(data.books);
      })
      .catch((e) => {
        if (cancelled) return;
        setError(e instanceof Error ? e.message : "Không tải được sách");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [token]);

  return (
    <>
      <div className="navbar">
        <div className="brand">📚 Bookstore</div>
        <div>
          <span className="who">Xin chào, {username ?? "..."}</span>
          <button className="btn" onClick={() => logout(token.id_token)}>
            Đăng xuất
          </button>
        </div>
      </div>
      <main>
        <div className="shop-header">
          <div>
            <h2>Danh sách sách</h2>
            <p className="hint">
              Đã đăng nhập qua Keycloak (SPI QR / hoặc username-password) — dữ liệu từ
              bookstore-api-mobile.
            </p>
          </div>
        </div>

        {error && <div className="alert">{error}</div>}

        {loading ? (
          <p className="hint">Đang tải sách…</p>
        ) : (
          <div className="book-grid">
            {books.map((b) => (
              <div className="book-card" key={b.id}>
                <div className="book-cover">{b.cover}</div>
                <div>
                  <div className="book-title">{b.title}</div>
                  <div className="book-author">{b.author}</div>
                </div>
                <span className="book-genre">{b.genre}</span>
                <div className="book-buy">
                  <span className="book-price">{b.price.toLocaleString("vi-VN")}₫</span>
                </div>
              </div>
            ))}
            {books.length === 0 && <p className="hint">Chưa có sách nào.</p>}
          </div>
        )}
      </main>
    </>
  );
}

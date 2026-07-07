import { FormEvent, useCallback, useEffect, useState } from "react";
import Navbar from "../components/Navbar";
import { useAuth } from "../context/AuthContext";
import { decodeJwtPayload } from "../utils/jwt";
import * as bookService from "../services/bookService";
import { ApiError, Book } from "../services/bookService";

// Trang quản lý sách — dữ liệu lấy từ bookstore-api (localhost:3003),
// mọi request đính kèm access token trong cookie; token hết hạn → về /login.

interface FormState {
  title: string;
  author: string;
  genre: string;
  price: string;
  cover: string;
}

const emptyForm: FormState = { title: "", author: "", genre: "", price: "", cover: "" };

export default function BookstorePage() {
  const { accessToken, setAccessToken, logout } = useAuth();
  const claims = accessToken ? decodeJwtPayload(accessToken) : null;

  const [books, setBooks] = useState<Book[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [formOpen, setFormOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState<FormState>(emptyForm);
  const [saving, setSaving] = useState(false);

  const handleApiError = useCallback(
    (err: unknown) => {
      if (err instanceof ApiError && err.status === 401) {
        setAccessToken(null); // token hết hạn/không hợp lệ → ProtectedRoute đẩy về /login
        return;
      }
      setError(err instanceof Error ? err.message : String(err));
    },
    [setAccessToken]
  );

  const loadBooks = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await bookService.getBooks();
      setBooks(data.books);
    } catch (err) {
      handleApiError(err);
    } finally {
      setLoading(false);
    }
  }, [handleApiError]);

  useEffect(() => {
    loadBooks();
  }, [loadBooks]);

  const openCreate = () => {
    setEditingId(null);
    setForm(emptyForm);
    setFormOpen(true);
  };

  const openEdit = (book: Book) => {
    setEditingId(book.id);
    setForm({
      title: book.title,
      author: book.author,
      genre: book.genre,
      price: String(book.price),
      cover: book.cover,
    });
    setFormOpen(true);
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  const closeForm = () => {
    setFormOpen(false);
    setEditingId(null);
    setForm(emptyForm);
  };

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setError(null);

    const input = {
      title: form.title.trim(),
      author: form.author.trim(),
      genre: form.genre.trim() || undefined,
      price: Number(form.price),
      cover: form.cover.trim() || undefined,
    };

    try {
      if (editingId === null) {
        const created = await bookService.createBook(input);
        setBooks((prev) => [...prev, created]);
      } else {
        const updated = await bookService.updateBook(editingId, input);
        setBooks((prev) => prev.map((b) => (b.id === updated.id ? updated : b)));
      }
      closeForm();
    } catch (err) {
      handleApiError(err);
    } finally {
      setSaving(false);
    }
  };

  const removeBook = async (book: Book) => {
    if (!window.confirm(`Xóa sách "${book.title}"?`)) return;
    setError(null);
    try {
      await bookService.deleteBook(book.id);
      setBooks((prev) => prev.filter((b) => b.id !== book.id));
      if (editingId === book.id) closeForm(); // đang sửa cuốn vừa xóa thì đóng form
    } catch (err) {
      handleApiError(err);
    }
  };

  const setField = (field: keyof FormState) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((prev) => ({ ...prev, [field]: e.target.value }));

  return (
    <>
      <Navbar claims={claims} onLogout={logout} />

      <main>
        <div className="shop-header">
          <div>
            <h2>Quản lý sách</h2>
            <p className="hint">
              Dữ liệu từ bookstore-api — xác thực bằng access token trong cookie.
            </p>
          </div>
          <button className="btn btn-blue" onClick={formOpen ? closeForm : openCreate}>
            {formOpen ? "Đóng form" : "＋ Thêm sách"}
          </button>
        </div>

        {error && <div className="alert">{error}</div>}

        {formOpen && (
          <form className="card" onSubmit={submit}>
            <h3>{editingId === null ? "Thêm sách mới" : `Sửa sách #${editingId}`}</h3>
            <div className="form-grid">
              <div>
                <label>Tiêu đề *</label>
                <input required value={form.title} onChange={setField("title")} />
              </div>
              <div>
                <label>Tác giả *</label>
                <input required value={form.author} onChange={setField("author")} />
              </div>
              <div>
                <label>Thể loại</label>
                <input value={form.genre} onChange={setField("genre")} placeholder="Khác" />
              </div>
              <div>
                <label>Giá (₫) *</label>
                <input required type="number" min="0" value={form.price} onChange={setField("price")} />
              </div>
              <div>
                <label>Bìa (emoji)</label>
                <input value={form.cover} onChange={setField("cover")} placeholder="📚" />
              </div>
            </div>
            <div className="form-actions">
              <button className="btn btn-blue" type="submit" disabled={saving}>
                {saving ? "Đang lưu…" : "💾 Lưu"}
              </button>
              <button className="btn" type="button" onClick={closeForm}>Hủy</button>
            </div>
          </form>
        )}

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
                  <span>
                    <button className="btn btn-sm" onClick={() => openEdit(b)}>✏️</button>
                    <button className="btn btn-sm btn-red" onClick={() => removeBook(b)}>🗑</button>
                  </span>
                </div>
              </div>
            ))}
            {books.length === 0 && (
              <p className="hint">Chưa có sách nào — hãy thêm sách mới.</p>
            )}
          </div>
        )}
      </main>
    </>
  );
}

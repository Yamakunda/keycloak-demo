import React, { useCallback, useEffect, useState } from "react";

// Backend giữ SAML session; FE chỉ gọi API kèm cookie (credentials: include).
const API_URL = process.env.REACT_APP_API_URL || "http://localhost:3033";

interface Me {
  username: string;
  roles: string[];
  isAdmin: boolean;
  attributes: Record<string, unknown>;
}

interface Book {
  id: number;
  title: string;
  author: string;
}

async function api(path: string, init?: RequestInit): Promise<Response> {
  return fetch(`${API_URL}${path}`, { credentials: "include", ...init });
}

function App() {
  const [me, setMe] = useState<Me | null>(null);
  const [books, setBooks] = useState<Book[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [title, setTitle] = useState("");
  const [author, setAuthor] = useState("");

  const loadBooks = useCallback(async () => {
    const res = await api("/api/books");
    if (res.ok) setBooks(await res.json());
  }, []);

  useEffect(() => {
    (async () => {
      if (new URLSearchParams(window.location.search).get("error") === "login_failed") {
        setError("SAML login failed — check the API logs.");
      }
      const res = await api("/api/me");
      if (res.ok) {
        setMe(await res.json());
        await loadBooks();
      }
      setLoading(false);
    })();
  }, [loadBooks]);

  const addBook = async (e: React.FormEvent) => {
    e.preventDefault();
    const res = await api("/api/books", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ title, author }),
    });
    if (res.ok) {
      setTitle("");
      setAuthor("");
      await loadBooks();
    } else {
      setError(`Add failed (HTTP ${res.status})`);
    }
  };

  const deleteBook = async (id: number) => {
    const res = await api(`/api/books/${id}`, { method: "DELETE" });
    if (res.ok || res.status === 404) await loadBooks();
    else setError(`Delete failed (HTTP ${res.status})`);
  };

  if (loading) return <div className="container">Loading…</div>;

  return (
    <div className="container">
      <div className="topbar">
        <h1>
          📚 Bookstore <span className="badge">SAML 2.0</span>
        </h1>
        <div>
          {me ? (
            <>
              Hi, <b>{me.username}</b>
              {me.roles.map((r) => (
                <span key={r} className="badge">
                  {r}
                </span>
              ))}{" "}
              — <a href={`${API_URL}/logout`}>Logout</a>
            </>
          ) : (
            <a className="button" href={`${API_URL}/login`}>
              Login with Keycloak (SAML)
            </a>
          )}
        </div>
      </div>

      {error && <p className="err">{error}</p>}

      {!me ? (
        <p>Welcome to the SAML-protected bookstore. Log in to see the catalogue.</p>
      ) : (
        <>
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>Title</th>
                <th>Author</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {books.map((b) => (
                <tr key={b.id}>
                  <td>{b.id}</td>
                  <td>{b.title}</td>
                  <td>{b.author}</td>
                  <td>
                    {me.isAdmin && (
                      <button className="danger" onClick={() => deleteBook(b.id)}>
                        Delete
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          {me.isAdmin ? (
            <>
              <h3>
                Add a book (requires <code>book-admin</code>)
              </h3>
              <form onSubmit={addBook}>
                <input
                  placeholder="Title"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  required
                />
                <input
                  placeholder="Author"
                  value={author}
                  onChange={(e) => setAuthor(e.target.value)}
                  required
                />
                <button type="submit">Add</button>
              </form>
            </>
          ) : (
            <p className="muted">
              You can view the catalogue. Adding/deleting needs the <code>book-admin</code> role.
            </p>
          )}

          <details>
            <summary>SAML assertion attributes (what Keycloak sent)</summary>
            <pre>{JSON.stringify(me.attributes, null, 2)}</pre>
          </details>
        </>
      )}
    </div>
  );
}

export default App;

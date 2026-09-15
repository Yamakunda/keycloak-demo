import { useEffect, useState } from "react";
import { decodePreferredUsername, handleCallback, TokenResponse } from "../services/authService";

interface Props {
  onLoggedIn: (token: TokenResponse, username?: string) => void;
}

export default function CallbackPage({ onLoggedIn }: Props) {
  const [error, setError] = useState("");

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const code = params.get("code");
    const state = params.get("state");
    const errorParam = params.get("error");

    if (errorParam) {
      setError(`Keycloak trả lỗi: ${errorParam} — ${params.get("error_description") ?? ""}`);
      return;
    }
    if (!code || !state) {
      setError("Thiếu code hoặc state trên URL callback.");
      return;
    }

    handleCallback(code, state)
      .then((token) => {
        const username = token.id_token ? decodePreferredUsername(token.id_token) : undefined;
        window.history.replaceState({}, "", "/");
        onLoggedIn(token, username);
      })
      .catch((e) => setError(e instanceof Error ? e.message : "Lỗi không xác định"));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div className="login-screen">
      <div className="login-box">
        {error ? (
          <>
            <div className="alert">{error}</div>
            <button className="btn btn-blue" onClick={() => (window.location.href = "/")}>
              Về trang đăng nhập
            </button>
          </>
        ) : (
          <>
            <div className="spinner" />
            <p>Đang hoàn tất đăng nhập…</p>
          </>
        )}
      </div>
    </div>
  );
}

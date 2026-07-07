import { useEffect, useRef, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import Spinner from "../components/Spinner";

// Route /callback — Keycloak redirect về đây với ?code=...&state=...
// Đổi code lấy token xong thì chuyển về trang chủ.
export default function CallbackPage() {
  const { handleCallback } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [error, setError] = useState<string | null>(null);
  const ran = useRef(false); // StrictMode chạy effect 2 lần — code chỉ đổi được 1 lần

  useEffect(() => {
    if (ran.current) return;
    ran.current = true;

    handleCallback(searchParams)
      .then(() => navigate("/", { replace: true }))
      .catch((err: unknown) => setError(err instanceof Error ? err.message : String(err)));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (error) {
    return (
      <div className="login-screen">
        <div className="login-box">
          <div className="logo">⚠️</div>
          <h2>Đăng nhập thất bại</h2>
          <div className="alert">{error}</div>
          <Link className="btn btn-blue" to="/login">← Về trang đăng nhập</Link>
        </div>
      </div>
    );
  }

  return <Spinner message="Đang đổi authorization code lấy token…" />;
}

import { Navigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

export default function LoginPage() {
  const { isAuthenticated, error, login } = useAuth();

  // Đã đăng nhập rồi thì về thẳng trang chủ
  if (isAuthenticated) return <Navigate to="/" replace />;

  return (
    <div className="login-screen">
      <div className="login-box">
        <div className="logo">📚</div>
        <h2>BookStore</h2>
        <p>
          Hệ thống nhà sách trực tuyến.
          <br />
          Vui lòng đăng nhập để tiếp tục.
        </p>
        {error && <div className="alert">{error}</div>}
        <button className="btn btn-blue" onClick={login}>
          Đăng nhập với Keycloak
        </button>
      </div>
    </div>
  );
}

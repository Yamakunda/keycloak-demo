import { useAuth } from "../context/AuthContext";

export default function LoginPage() {
  const { login } = useAuth();
  // Backend redirect về đây kèm ?error=... nếu đổi code lấy token thất bại
  // (xem bookstore-api-qr-2/src/routes/auth.js failFrontend).
  const error = new URLSearchParams(window.location.search).get("error");

  return (
    <div className="login-screen">
      <div className="login-box">
        <div className="logo">🔑</div>
        <h2>Test đăng nhập Keycloak SPI</h2>
        <p>
          Bấm nút bên dưới để chuyển sang <b>trang login mặc định của Keycloak</b>. Trên
          trang đó, bấm <b>"Try another way"</b> rồi chọn <b>"Đăng nhập bằng QR"</b> để
          test Authenticator SPI — dùng app di động đã đăng nhập sẵn để quét mã.
        </p>
        {error && <div className="alert">{error}</div>}
        <button className="btn btn-blue" onClick={login}>
          Đăng nhập qua Keycloak
        </button>
      </div>
    </div>
  );
}

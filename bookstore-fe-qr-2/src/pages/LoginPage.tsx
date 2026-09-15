import { useState } from "react";
import { startLogin } from "../services/authService";

export default function LoginPage() {
  const [error, setError] = useState("");

  const onClick = () => {
    setError("");
    startLogin().catch((e) => setError(e instanceof Error ? e.message : "Lỗi không xác định"));
  };

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
        <button className="btn btn-blue" onClick={onClick}>
          Đăng nhập qua Keycloak
        </button>
      </div>
    </div>
  );
}

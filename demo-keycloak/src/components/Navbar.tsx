import { JwtClaims } from "../utils/jwt";

interface NavbarProps {
  claims: JwtClaims | null;
  onLogout: () => void;
}

export default function Navbar({ claims, onLogout }: NavbarProps) {
  return (
    <nav className="navbar">
      <span className="brand">📚 BookStore</span>
      <div>
        <span className="who">
          Xin chào, <strong>{claims?.name || claims?.preferred_username}</strong>
        </span>
        <button className="btn btn-sm btn-red" onClick={onLogout}>Đăng xuất</button>
      </div>
    </nav>
  );
}

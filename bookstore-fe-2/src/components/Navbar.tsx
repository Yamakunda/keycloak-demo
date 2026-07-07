import { UserInfo } from "../services/authService";

interface NavbarProps {
  user: UserInfo | null;
  onLogout: () => void;
}

export default function Navbar({ user, onLogout }: NavbarProps) {
  return (
    <nav className="navbar">
      <span className="brand">📚 BookStore</span>
      <div>
        <span className="who">
          Xin chào, <strong>{user?.name || user?.preferred_username}</strong>
        </span>
        <button className="btn btn-sm btn-red" onClick={onLogout}>Đăng xuất</button>
      </div>
    </nav>
  );
}

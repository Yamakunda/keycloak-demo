import { ReactNode } from "react";
import { Navigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

// Chặn route yêu cầu đăng nhập — chưa có token thì đẩy về /login. Trong lúc gọi /me lần
// đầu (checking) chưa biết trạng thái, giữ nguyên trang trắng ngắn thay vì đẩy nhầm.
export default function ProtectedRoute({ children }: { children: ReactNode }) {
  const { isAuthenticated, checking } = useAuth();
  if (checking) return null;
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  return <>{children}</>;
}

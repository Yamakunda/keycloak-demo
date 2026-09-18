import { createContext, useContext, useEffect, useState, ReactNode } from "react";
import * as authService from "../services/authService";
import type { UserInfo } from "../services/authService";

// AuthContext — state đăng nhập dùng chung cho mọi route.
// access_token nằm trong cookie httpOnly (backend set) nên FE không đọc được token;
// trạng thái đăng nhập được suy ra từ GET /api/auth/me (cookie tự gửi kèm theo request).

interface AuthContextValue {
  user: UserInfo | null;
  isAuthenticated: boolean;
  checking: boolean;
  login: () => void;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserInfo | null>(null);
  const [checking, setChecking] = useState(true); // đang gọi /me lần đầu để biết đã đăng nhập chưa

  useEffect(() => {
    authService
      .fetchMe()
      .then(setUser)
      .finally(() => setChecking(false));
  }, []);

  const logout = async () => {
    await authService.logout();
    setUser(null);
  };

  const value: AuthContextValue = {
    user,
    isAuthenticated: !!user,
    checking,
    login: authService.login,
    logout,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth phải được dùng bên trong <AuthProvider>");
  return ctx;
}

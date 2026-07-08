import { createContext, useContext, useEffect, useState, ReactNode } from "react";
import * as authService from "../services/authService";
import type { UserInfo } from "../services/authService";
import Spinner from "../components/Spinner";

// AuthContext — state đăng nhập dùng chung cho mọi route.
// access_token nằm trong cookie httpOnly (backend set) nên FE không đọc được token;
// trạng thái đăng nhập được suy ra từ GET /api/auth/me (cookie tự gửi kèm theo request).

interface AuthContextValue {
  user: UserInfo | null;
  isAuthenticated: boolean;
  error: string | null;
  setError: (message: string | null) => void;
  login: () => void;
  logout: () => Promise<void>;
  refresh: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function errorMessage(err: unknown): string {
  return err instanceof Error ? err.message : String(err);
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserInfo | null>(null);
  const [checking, setChecking] = useState(true); // đang gọi /me lần đầu để biết đã đăng nhập chưa
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    authService
      .fetchMe()
      .then(setUser)
      .finally(() => setChecking(false));
  }, []);

  const refresh = async () => {
    setError(null);
    try {
      await authService.refreshTokens();
      const me = await authService.fetchMe();
      setUser(me);
    } catch (err) {
      setError(errorMessage(err));
    }
  };

  const logout = async () => {
    await authService.logout();
    setUser(null);
  };

  const value: AuthContextValue = {
    user,
    isAuthenticated: !!user,
    error,
    setError,
    login: authService.login,
    logout,
    refresh,
  };

  if (checking) return <Spinner message="Đang kiểm tra phiên đăng nhập…" />;

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth phải được dùng bên trong <AuthProvider>");
  return ctx;
}

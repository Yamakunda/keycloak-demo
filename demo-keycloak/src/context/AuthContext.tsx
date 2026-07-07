import { createContext, useContext, useState, ReactNode } from "react";
import * as authService from "../services/authService";

// AuthContext — state đăng nhập dùng chung cho mọi route.
// Logic gọi Keycloak nằm ở services/authService; đây chỉ là lớp state.

interface AuthContextValue {
  accessToken: string | null;
  setAccessToken: (token: string | null) => void;
  isAuthenticated: boolean;
  error: string | null;
  setError: (message: string | null) => void;
  steps: string[];
  logStep: (msg: string) => void;
  handleCallback: (query: URLSearchParams) => Promise<void>;
  login: () => void;
  logout: () => void;
  refresh: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function errorMessage(err: unknown): string {
  return err instanceof Error ? err.message : String(err);
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [accessToken, setAccessToken] = useState<string | null>(() => authService.getAccessToken());
  const [error, setError] = useState<string | null>(null);
  const [steps, setSteps] = useState<string[]>([]); // log các bước OAuth2 đã chạy

  const logStep = (msg: string) => setSteps((prev) => [...prev, msg]);

  // Được CallbackPage gọi khi Keycloak redirect về /callback?code=...&state=...
  const handleCallback = async (query: URLSearchParams) => {
    if (query.has("error")) {
      throw new Error(`${query.get("error")}: ${query.get("error_description") || ""}`);
    }
    logStep("Nhận authorization code từ Keycloak (/callback?code=...)");
    const tokens = await authService.exchangeCode(query.get("code"), query.get("state"));
    logStep("POST /token với code → nhận access_token, id_token, refresh_token");
    logStep(`Lưu access_token vào cookie (max-age=${tokens.expires_in}s)`);
    setAccessToken(tokens.access_token);
  };

  const refresh = async () => {
    setError(null);
    try {
      const tokens = await authService.refreshTokens();
      setAccessToken(tokens.access_token);
      logStep("POST /token (grant_type=refresh_token) → access_token mới, cập nhật cookie");
    } catch (err) {
      setError(errorMessage(err));
    }
  };

  const value: AuthContextValue = {
    accessToken,
    setAccessToken,
    isAuthenticated: !!accessToken,
    error,
    setError,
    steps,
    logStep,
    handleCallback,
    login: authService.login,
    logout: authService.logout,
    refresh,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth phải được dùng bên trong <AuthProvider>");
  return ctx;
}

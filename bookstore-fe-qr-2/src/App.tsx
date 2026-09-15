import { useState } from "react";
import "./App.css";
import LoginPage from "./pages/LoginPage";
import CallbackPage from "./pages/CallbackPage";
import BooksPage from "./pages/BooksPage";
import { TokenResponse } from "./services/authService";

export default function App() {
  const [token, setToken] = useState<TokenResponse | null>(null);
  const [username, setUsername] = useState<string | undefined>();

  if (window.location.pathname === "/callback" && !token) {
    return (
      <CallbackPage
        onLoggedIn={(t, u) => {
          setToken(t);
          setUsername(u);
        }}
      />
    );
  }

  if (token) {
    return <BooksPage token={token} username={username} />;
  }

  return <LoginPage />;
}

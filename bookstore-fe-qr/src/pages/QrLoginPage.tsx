import { useCallback, useEffect, useRef, useState } from "react";
import QRCode from "qrcode";
import {
  DeviceStartResponse,
  TokenResponse,
  pollDeviceLogin,
  startDeviceLogin,
} from "../services/deviceService";
import { Book, getBooks } from "../services/bookService";
import { decodePreferredUsername } from "../utils/jwt";

type Phase = "loading" | "ready" | "approved" | "expired" | "denied" | "error";

export default function QrLoginPage() {
  const [phase, setPhase] = useState<Phase>("loading");
  const [device, setDevice] = useState<DeviceStartResponse | null>(null);
  const [token, setToken] = useState<TokenResponse | null>(null);
  const [secondsLeft, setSecondsLeft] = useState(0);
  const [errorMsg, setErrorMsg] = useState("");
  const [username, setUsername] = useState<string | undefined>();
  const [books, setBooks] = useState<Book[]>([]);
  const [booksLoading, setBooksLoading] = useState(false);
  const [booksError, setBooksError] = useState("");
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const pollTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const clearPollTimer = () => {
    if (pollTimer.current) {
      clearTimeout(pollTimer.current);
      pollTimer.current = null;
    }
  };

  const begin = useCallback(async () => {
    clearPollTimer();
    setPhase("loading");
    setErrorMsg("");
    setToken(null);
    setUsername(undefined);
    setBooks([]);
    setBooksError("");
    try {
      const started = await startDeviceLogin();
      setDevice(started);
      setSecondsLeft(started.expires_in);
      setPhase("ready");
    } catch (e) {
      setErrorMsg(e instanceof Error ? e.message : "Lỗi không xác định");
      setPhase("error");
    }
  }, []);

  useEffect(() => {
    begin();
    return clearPollTimer;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Vẽ QR mỗi khi có device mới
  useEffect(() => {
    if (device && canvasRef.current) {
      QRCode.toCanvas(canvasRef.current, device.verification_uri_complete, {
        width: 240,
        margin: 1,
      });
    }
  }, [device]);

  // Đếm ngược hết hạn QR
  useEffect(() => {
    if (phase !== "ready") return;
    if (secondsLeft <= 0) {
      setPhase("expired");
      clearPollTimer();
      return;
    }
    const t = setTimeout(() => setSecondsLeft((s) => s - 1), 1000);
    return () => clearTimeout(t);
  }, [phase, secondsLeft]);

  // Polling trạng thái device code
  useEffect(() => {
    if (phase !== "ready" || !device) return;

    let cancelled = false;
    let interval = device.interval * 1000;

    const tick = async () => {
      const result = await pollDeviceLogin(device.device_code);
      if (cancelled) return;

      switch (result.status) {
        case "approved":
          setToken(result.token);
          setUsername(decodePreferredUsername(result.token.access_token));
          setPhase("approved");
          return;
        case "expired":
          setPhase("expired");
          return;
        case "denied":
          setPhase("denied");
          return;
        case "slow_down":
          interval += 5000;
          break;
        case "pending":
        default:
          break;
      }
      pollTimer.current = setTimeout(tick, interval);
    };

    pollTimer.current = setTimeout(tick, interval);
    return () => {
      cancelled = true;
      clearPollTimer();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [phase, device]);

  // Sau khi đăng nhập thành công, tải danh sách sách bằng access token vừa nhận
  useEffect(() => {
    if (phase !== "approved" || !token) return;
    let cancelled = false;
    setBooksLoading(true);
    setBooksError("");
    getBooks(token.access_token)
      .then((data) => {
        if (cancelled) return;
        setBooks(data.books);
        setUsername((prev) => prev ?? data.authenticatedAs);
      })
      .catch((e) => {
        if (cancelled) return;
        setBooksError(e instanceof Error ? e.message : "Không tải được sách");
      })
      .finally(() => {
        if (!cancelled) setBooksLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [phase, token]);

  if (phase === "approved" && token) {
    return (
      <>
        <div className="navbar">
          <div className="brand">📚 Bookstore</div>
          <div>
            <span className="who">Xin chào, {username ?? "..."}</span>
            <button className="btn" onClick={begin}>Đăng nhập lại</button>
          </div>
        </div>
        <main>
          <div className="shop-header">
            <div>
              <h2>Danh sách sách</h2>
              <p className="hint">Đã đăng nhập qua QR — dữ liệu từ bookstore-api-mobile.</p>
            </div>
          </div>

          {booksError && <div className="alert">{booksError}</div>}

          {booksLoading ? (
            <p className="hint">Đang tải sách…</p>
          ) : (
            <div className="book-grid">
              {books.map((b) => (
                <div className="book-card" key={b.id}>
                  <div className="book-cover">{b.cover}</div>
                  <div>
                    <div className="book-title">{b.title}</div>
                    <div className="book-author">{b.author}</div>
                  </div>
                  <span className="book-genre">{b.genre}</span>
                  <div className="book-buy">
                    <span className="book-price">{b.price.toLocaleString("vi-VN")}₫</span>
                  </div>
                </div>
              ))}
              {books.length === 0 && (
                <p className="hint">Chưa có sách nào.</p>
              )}
            </div>
          )}
        </main>
      </>
    );
  }

  return (
    <div className="login-screen">
      <div className="login-box">
        <div className="logo">📱➜💻</div>
        <h2>Đăng nhập bằng QR</h2>
        <p>
          Mở app trên điện thoại đã đăng nhập, chọn <b>Quét mã QR</b> để xác nhận
          đăng nhập trên thiết bị này.
        </p>

        {phase === "loading" && <div className="spinner" />}

        {errorMsg && <div className="alert">{errorMsg}</div>}

        {phase === "ready" && device && (
          <>
            <canvas ref={canvasRef} style={{ margin: "0 auto", display: "block" }} />
            <p className="hint">
              Hoặc nhập mã: <code>{device.user_code}</code>
            </p>
            <p className="hint">Mã hết hạn sau {secondsLeft}s</p>
          </>
        )}

        {phase === "expired" && (
          <>
            <div className="alert">Mã QR đã hết hạn.</div>
            <button className="btn btn-blue" onClick={begin}>
              Tạo mã mới
            </button>
          </>
        )}

        {phase === "denied" && (
          <>
            <div className="alert">Yêu cầu đăng nhập đã bị từ chối.</div>
            <button className="btn btn-blue" onClick={begin}>
              Thử lại
            </button>
          </>
        )}

        {phase === "error" && (
          <button className="btn btn-blue" onClick={begin}>
            Thử lại
          </button>
        )}
      </div>
    </div>
  );
}

import { useCallback, useEffect, useRef, useState } from "react";
import QRCode from "qrcode";
import {
  DeviceStartResponse,
  TokenResponse,
  pollDeviceLogin,
  startDeviceLogin,
} from "../services/deviceService";

type Phase = "loading" | "ready" | "approved" | "expired" | "denied" | "error";

export default function QrLoginPage() {
  const [phase, setPhase] = useState<Phase>("loading");
  const [device, setDevice] = useState<DeviceStartResponse | null>(null);
  const [token, setToken] = useState<TokenResponse | null>(null);
  const [secondsLeft, setSecondsLeft] = useState(0);
  const [errorMsg, setErrorMsg] = useState("");
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

        {phase === "approved" && token && (
          <>
            <div className="alert" style={{ background: "#dcfce7", color: "#166534", borderColor: "#86efac" }}>
              Đăng nhập thành công!
            </div>
            <p className="hint">Access token:</p>
            <pre className="token">{token.access_token}</pre>
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

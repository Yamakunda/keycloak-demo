export default function Spinner({ message }: { message: string }) {
  return (
    <div className="login-screen">
      <div className="login-box">
        <div className="spinner" />
        <p>{message}</p>
      </div>
    </div>
  );
}

import { useState, FormEvent } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../store/AuthContext";

export default function Login() {
  const { login } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await login(email, password);
    } catch (err: any) {
      setError(err.message || "Login failed");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="auth-wrap">
      <div className="form-card">
        <div className="brand" style={{ marginBottom: 4 }}>Stock<span style={{ color: "var(--amber)" }}>Flow</span></div>
        <div className="subtitle" style={{ marginBottom: 24 }}>Sign in to continue</div>
        {error && <div className="error-banner">{error}</div>}
        <form onSubmit={onSubmit}>
          <div className="field">
            <label>Email</label>
            <input value={email} onChange={(e) => setEmail(e.target.value)} type="email" required />
          </div>
          <div className="field">
            <label>Password</label>
            <input value={password} onChange={(e) => setPassword(e.target.value)} type="password" required />
          </div>
          <button className="btn" type="submit" disabled={loading} style={{ width: "100%" }}>
            {loading ? "Signing in..." : "Sign in"}
          </button>
        </form>
        <div style={{ marginTop: 16, fontSize: 13, color: "var(--text-dim)" }}>
          No account? <Link to="/register">Create one</Link>
        </div>
      </div>
    </div>
  );
}

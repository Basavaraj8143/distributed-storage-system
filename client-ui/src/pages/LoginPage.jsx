import { useState } from "react";
import { login } from "../api";
import "../login.css";

export default function LoginPage({ notice, onSuccess }) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function handleSubmit(event) {
    event.preventDefault();
    if (!username.trim() || !password) {
      setError("Enter your username and password.");
      return;
    }

    setBusy(true);
    setError("");
    try {
      await login(username.trim(), password);
      onSuccess();
    } catch (err) {
      setError(err.message || "Could not sign in.");
      setBusy(false);
    }
  }

  return (
    <div className="login-wrap">
      <form className="login-card" onSubmit={handleSubmit}>
        <p className="eyebrow">Byte Harvest</p>
        <h2>Sign in</h2>
        <p className="page-copy">Use the login set for this cluster (APP_USERNAME and APP_PASSWORD).</p>

        {notice ? <p className="message error">{notice}</p> : null}

        <label className="field-label" htmlFor="login-username">
          Username
        </label>
        <input
          id="login-username"
          className="text-input"
          type="text"
          autoComplete="username"
          autoFocus
          value={username}
          onChange={(event) => setUsername(event.target.value)}
          disabled={busy}
        />

        <label className="field-label" htmlFor="login-password">
          Password
        </label>
        <input
          id="login-password"
          className="text-input"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          disabled={busy}
        />

        {error ? <p className="message error">{error}</p> : null}

        <button className="primary-button" type="submit" disabled={busy}>
          {busy ? "Signing in..." : "Sign in"}
        </button>
      </form>
    </div>
  );
}

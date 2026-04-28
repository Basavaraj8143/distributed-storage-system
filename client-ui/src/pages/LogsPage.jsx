import { useCallback, useEffect, useState } from "react";
import { getLogs } from "../api";

const POLL_MS = 5000;
const LIMIT = 150;

function formatTime(ts) {
  const d = new Date(ts);
  if (Number.isNaN(d.getTime())) return String(ts || "-");
  return d.toLocaleString();
}

export default function LogsPage() {
  const [level, setLevel] = useState("ALL");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [logs, setLogs] = useState([]);
  const [lastUpdated, setLastUpdated] = useState("");

  const refresh = useCallback(async () => {
    try {
      const data = await getLogs(level, LIMIT);
      setLogs(Array.isArray(data) ? data : []);
      setLastUpdated(new Date().toLocaleTimeString());
      setError("");
    } catch (err) {
      setError(err.message || "Failed to load logs.");
    } finally {
      setLoading(false);
    }
  }, [level]);

  useEffect(() => {
    setLoading(true);
    refresh();
    const id = setInterval(refresh, POLL_MS);
    return () => clearInterval(id);
  }, [refresh]);

  return (
    <section className="page">
      <p className="eyebrow">Operations</p>
      <h2>Logs</h2>
      <p className="page-copy">Structured runtime events with 5-second auto refresh.</p>

      <div className="section-head">
        <h3>Event Stream</h3>
        <div className="logs-controls">
          <select className="text-input" value={level} onChange={(e) => setLevel(e.target.value)}>
            <option value="ALL">ALL</option>
            <option value="INFO">INFO</option>
            <option value="WARN">WARN</option>
            <option value="ERROR">ERROR</option>
          </select>
          <button className="ghost-button" type="button" onClick={refresh} disabled={loading}>
            {loading ? "Refreshing..." : "Refresh"}
          </button>
        </div>
      </div>

      {error ? <p className="message error">{error}</p> : null}

      {logs.length === 0 && !loading ? (
        <article className="placeholder-panel">No log events yet.</article>
      ) : (
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Timestamp</th>
                <th>Level</th>
                <th>Component</th>
                <th>Message</th>
              </tr>
            </thead>
            <tbody>
              {logs.map((item, idx) => (
                <tr key={`${item.timestamp || "ts"}-${idx}`}>
                  <td>{formatTime(item.timestamp)}</td>
                  <td>
                    <span className={`status-dot ${String(item.level).toUpperCase() === "INFO" ? "good" : String(item.level).toUpperCase() === "WARN" ? "warn" : "bad"}`}>
                      {String(item.level || "-").toUpperCase()}
                    </span>
                  </td>
                  <td>{item.component || "-"}</td>
                  <td>{item.message || "-"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <p className="field-note">Last updated: {lastUpdated || "-"}</p>
    </section>
  );
}

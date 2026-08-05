import { useCallback, useEffect, useState } from "react";
import { getLogs, getMasterLogs } from "../api";

const POLL_MS = 5000;
const LIMIT = 150;
const MASTER_LOG_LINES = 500;

function formatTime(ts) {
  const d = new Date(ts);
  if (Number.isNaN(d.getTime())) return String(ts || "-");
  return d.toLocaleString();
}

export default function LogsPage() {
  const [view, setView] = useState("events");
  const [level, setLevel] = useState("ALL");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [logs, setLogs] = useState([]);
  const [masterLogs, setMasterLogs] = useState("");
  const [lastUpdated, setLastUpdated] = useState("");

  const refresh = useCallback(async () => {
    try {
      if (view === "master") {
        const data = await getMasterLogs(MASTER_LOG_LINES);
        setMasterLogs(data || "");
      } else {
        const data = await getLogs(level, LIMIT);
        setLogs(Array.isArray(data) ? data : []);
      }
      setLastUpdated(new Date().toLocaleTimeString());
      setError("");
    } catch (err) {
      setError(err.message || "Failed to load logs.");
    } finally {
      setLoading(false);
    }
  }, [level, view]);

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
      <p className="page-copy">Structured events and master terminal logs with 5-second auto refresh.</p>

      <div className="section-head">
        <h3>{view === "master" ? "Master Logs" : "Event Stream"}</h3>
        <div className="logs-controls">
          <button className={view === "events" ? "primary-button compact" : "ghost-button"} type="button" onClick={() => setView("events")}>
            Events
          </button>
          <button className={view === "master" ? "primary-button compact" : "ghost-button"} type="button" onClick={() => setView("master")}>
            Master Logs
          </button>
          {view === "events" ? (
            <select className="text-input" value={level} onChange={(e) => setLevel(e.target.value)}>
              <option value="ALL">ALL</option>
              <option value="INFO">INFO</option>
              <option value="WARN">WARN</option>
              <option value="ERROR">ERROR</option>
            </select>
          ) : null}
          <button className="ghost-button" type="button" onClick={refresh} disabled={loading}>
            {loading ? "Refreshing..." : "Refresh"}
          </button>
        </div>
      </div>

      {error ? <p className="message error">{error}</p> : null}

      {view === "master" ? (
        <pre className="master-log-panel">{masterLogs || "No master log lines available yet."}</pre>
      ) : logs.length === 0 && !loading ? (
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

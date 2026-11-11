import { useCallback, useEffect, useState } from "react";
import { getNodeStatus } from "../api";
import { usePolling } from "../hooks/usePolling";

const POLL_MS = 10000;

function toDate(value) {
  if (value === null || value === undefined || value === "") return null;
  const date = typeof value === "number" || /^\d+$/.test(String(value)) ? new Date(Number(value)) : new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

function toDisplayTime(value) {
  if (value === null || value === undefined || value === "") return "-";
  const date = toDate(value);
  return date ? date.toLocaleString() : String(value);
}

// "12s ago", "3m 05s ago" ... so a node going quiet is visible as it happens.
function toAge(value, now) {
  const date = toDate(value);
  if (!date) return "";
  const seconds = Math.max(0, Math.round((now - date.getTime()) / 1000));
  if (seconds < 60) return `${seconds}s ago`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m ${String(seconds % 60).padStart(2, "0")}s ago`;
  return `${Math.floor(minutes / 60)}h ${String(minutes % 60).padStart(2, "0")}m ago`;
}

function normalizeNode(node, index) {
  const status = String(node.status || node.state || "UNKNOWN").toUpperCase();
  return {
    nodeId: node.nodeId || node.id || `node-${index + 1}`,
    status,
    lastHeartbeat: node.lastHeartbeat || node.lastSeen || null,
    nodeUrl: node.nodeUrl || node.url || "-"
  };
}

function statusTone(status) {
  if (status === "ACTIVE") return "good";
  if (status === "STALE") return "warn";
  return "bad";
}

export default function NodeStatusPage() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [nodes, setNodes] = useState([]);
  const [lastUpdated, setLastUpdated] = useState("");
  const [now, setNow] = useState(() => Date.now());

  // Tick every second so the "seen X ago" column moves between the 10-second data refreshes.
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, []);

  const refresh = useCallback(async () => {
    try {
      const raw = await getNodeStatus();
      const list = Array.isArray(raw) ? raw : [];
      setNodes(list.map(normalizeNode));
      setLastUpdated(new Date().toLocaleTimeString());
      setError("");
    } catch (err) {
      setError(err.message || "Failed to load node status.");
    } finally {
      setLoading(false);
    }
  }, []);

  usePolling(refresh, POLL_MS);

  return (
    <section className="page">
      <p className="eyebrow">Operations</p>
      <h2>Node Status</h2>
      <p className="page-copy">
        Node health view with 10-second auto refresh. A STALE node has gone quiet: it gets no new uploads, but its data
        is kept. A FAILED node has been silent for longer: its replicas are dropped and its chunks are re-replicated.
      </p>

      <div className="section-head">
        <h3>Node Health</h3>
        <button className="ghost-button" type="button" onClick={refresh} disabled={loading}>
          {loading ? "Refreshing..." : "Refresh"}
        </button>
      </div>

      {error ? <p className="message error">{error}</p> : null}

      {nodes.length === 0 && !loading ? (
        <article className="placeholder-panel">No node data available.</article>
      ) : (
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Node ID</th>
                <th>Status</th>
                <th>Last Heartbeat</th>
                <th>Last Seen</th>
                <th>Node URL</th>
              </tr>
            </thead>
            <tbody>
              {nodes.map((node) => (
                <tr key={node.nodeId}>
                  <td>{node.nodeId}</td>
                  <td>
                    <span className={`status-dot ${statusTone(node.status)}`}>{node.status}</span>
                  </td>
                  <td>{toDisplayTime(node.lastHeartbeat)}</td>
                  <td>{toAge(node.lastHeartbeat, now) || "-"}</td>
                  <td>{node.nodeUrl}</td>
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

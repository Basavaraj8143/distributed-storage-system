import { useCallback, useEffect, useState } from "react";
import { getNodeStatus } from "../api";

const POLL_MS = 10000;

function toDisplayTime(value) {
  if (value === null || value === undefined || value === "") return "-";
  const date = typeof value === "number" || /^\d+$/.test(String(value)) ? new Date(Number(value)) : new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return date.toLocaleString();
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

export default function NodeStatusPage() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [nodes, setNodes] = useState([]);
  const [lastUpdated, setLastUpdated] = useState("");

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

  useEffect(() => {
    refresh();
    const id = setInterval(refresh, POLL_MS);
    return () => clearInterval(id);
  }, [refresh]);

  return (
    <section className="page">
      <p className="eyebrow">Operations</p>
      <h2>Node Status</h2>
      <p className="page-copy">Node health view with 10-second auto refresh.</p>

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
                <th>Node URL</th>
              </tr>
            </thead>
            <tbody>
              {nodes.map((node) => (
                <tr key={node.nodeId}>
                  <td>{node.nodeId}</td>
                  <td>
                    <span className={node.status === "ACTIVE" ? "status-dot good" : "status-dot bad"}>{node.status}</span>
                  </td>
                  <td>{toDisplayTime(node.lastHeartbeat)}</td>
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

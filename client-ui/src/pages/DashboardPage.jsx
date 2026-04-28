import { useCallback, useEffect, useMemo, useState } from "react";
import { getNodeStatus, getSystemHealth } from "../api";

const POLL_MS = 10000;

function normalizeHealth(raw, nodes = []) {
  const health = raw && typeof raw === "object" ? raw : {};
  const activeNodes =
    Number.isFinite(health.activeNodes) ? health.activeNodes : nodes.filter((n) => String(n.status).toUpperCase() === "ACTIVE").length;
  const failedNodes =
    Number.isFinite(health.failedNodes) ? health.failedNodes : nodes.filter((n) => String(n.status).toUpperCase() !== "ACTIVE").length;

  let status = health.status || health.clusterStatus || "";
  if (!status) {
    status = failedNodes > 0 ? "DEGRADED" : "HEALTHY";
  }

  return { activeNodes, failedNodes, status: String(status).toUpperCase() };
}

export default function DashboardPage() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [health, setHealth] = useState({ activeNodes: 0, failedNodes: 0, status: "UNKNOWN" });
  const [lastUpdated, setLastUpdated] = useState("");

  const refresh = useCallback(async () => {
    try {
      const [healthRaw, nodesRaw] = await Promise.all([getSystemHealth(), getNodeStatus()]);
      const nodes = Array.isArray(nodesRaw) ? nodesRaw : [];
      setHealth(normalizeHealth(healthRaw, nodes));
      setLastUpdated(new Date().toLocaleTimeString());
      setError("");
    } catch (err) {
      setError(err.message || "Failed to load system health.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refresh();
    const id = setInterval(refresh, POLL_MS);
    return () => clearInterval(id);
  }, [refresh]);

  const statusClass = useMemo(() => {
    if (health.status === "HEALTHY") return "status-pill good";
    if (health.status === "DEGRADED") return "status-pill warn";
    return "status-pill bad";
  }, [health.status]);

  return (
    <section className="page">
      <p className="eyebrow">Overview</p>
      <h2>Dashboard</h2>
      <p className="page-copy">Live cluster summary with 10-second auto refresh.</p>

      <div className="section-head">
        <h3>System Snapshot</h3>
        <button className="ghost-button" type="button" onClick={refresh} disabled={loading}>
          {loading ? "Refreshing..." : "Refresh"}
        </button>
      </div>

      {error ? <p className="message error">{error}</p> : null}

      <div className="stats-grid">
        <article className="stats-card">
          <p className="stats-label">Cluster Status</p>
          <p className={statusClass}>{health.status}</p>
        </article>
        <article className="stats-card">
          <p className="stats-label">Active Nodes</p>
          <p className="stats-value">{health.activeNodes}</p>
        </article>
        <article className="stats-card">
          <p className="stats-label">Failed Nodes</p>
          <p className="stats-value">{health.failedNodes}</p>
        </article>
      </div>

      <p className="field-note">Last updated: {lastUpdated || "-"}</p>
    </section>
  );
}

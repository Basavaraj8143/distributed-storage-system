import { useCallback, useEffect, useState } from "react";
import { getNodeStatus, getLogs, simulateNodeOffline, simulateNodeOnline } from "../api";

const POLL_INTERVAL = 5000;
const EVENT_COMPONENTS = new Set(["HEARTBEAT", "REPAIR", "INTEGRITY"]);

function formatHeartbeat(value) {
  if (value === null || value === undefined || value === "") return "No heartbeat recorded";
  const date = typeof value === "number" || /^\d+$/.test(String(value)) ? new Date(Number(value)) : new Date(value);
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleTimeString();
}

function formatEventTime(value) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "--:--:--" : date.toLocaleTimeString();
}

function normalizeNode(node, index) {
  return {
    nodeId: node.nodeId || node.id || `node-${index + 1}`,
    nodeUrl: node.nodeUrl || node.url || "Address unavailable",
    lastHeartbeat: node.lastHeartbeat || node.lastSeen || null,
    status: String(node.status || node.state || "UNKNOWN").toUpperCase()
  };
}

export default function SimulationPage() {
  const [nodes, setNodes] = useState([]);
  const [events, setEvents] = useState([]);
  const [simulatedOffline, setSimulatedOffline] = useState({});
  const [pendingNode, setPendingNode] = useState("");
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [lastUpdated, setLastUpdated] = useState(null);

  const refresh = useCallback(async (background = false) => {
    if (background) setRefreshing(true);
    try {
      const [nodeData, logData] = await Promise.all([getNodeStatus(), getLogs("ALL", 100)]);
      setNodes((Array.isArray(nodeData) ? nodeData : []).map(normalizeNode));
      setEvents((Array.isArray(logData) ? logData : []).filter((event) => EVENT_COMPONENTS.has(event.component)).slice(0, 40));
      setError("");
      setLastUpdated(new Date());
    } catch (requestError) {
      setError(requestError.message || "Could not load cluster state.");
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    refresh();
    const intervalId = window.setInterval(() => refresh(true), POLL_INTERVAL);
    return () => window.clearInterval(intervalId);
  }, [refresh]);

  const changeNodeState = async (node) => {
    const takingOffline = !simulatedOffline[node.nodeId];
    setPendingNode(node.nodeId);
    setNotice("");
    try {
      if (takingOffline) {
        await simulateNodeOffline(node.nodeId);
        setSimulatedOffline((current) => ({ ...current, [node.nodeId]: true }));
        setNotice(`${node.nodeId} has been instructed to go offline. It turns STALE after a short silence and FAILED after a longer one, which is when repair starts.`);
      } else {
        await simulateNodeOnline(node.nodeId);
        setSimulatedOffline((current) => ({ ...current, [node.nodeId]: false }));
        setNotice(`${node.nodeId} is back online. Waiting for its next heartbeat.`);
      }
      await refresh(true);
    } catch (requestError) {
      setError(requestError.message || `Could not change ${node.nodeId} state.`);
    } finally {
      setPendingNode("");
    }
  };

  const activeCount = nodes.filter((node) => node.status === "ACTIVE" && !simulatedOffline[node.nodeId]).length;
  const failedCount = nodes.filter((node) => node.status === "FAILED" || simulatedOffline[node.nodeId]).length;
  const unknownCount = Math.max(0, nodes.length - activeCount - failedCount);
  const clusterState = nodes.length === 0 ? "Awaiting nodes" : failedCount ? "Degraded" : unknownCount ? "Checking nodes" : "All systems operational";

  return (
    <section className="page simulator-page">
      <div className="simulator-heading">
        <div>
          <p className="eyebrow">Operations / Resilience lab</p>
          <h2>Chaos Simulator</h2>
          <p className="page-copy">Take a storage node offline and watch the cluster detect and respond to the failure.</p>
        </div>
        <button className="ghost-button simulator-refresh" type="button" onClick={() => refresh(true)} disabled={refreshing}>
          <span aria-hidden="true">↻</span> {refreshing ? "Refreshing…" : "Refresh cluster"}
        </button>
      </div>

      {error ? <div className="simulator-message simulator-error" role="alert"><strong>Connection issue</strong><span>{error}</span></div> : null}
      {notice ? <div className="simulator-message simulator-notice" role="status"><strong>Action sent</strong><span>{notice}</span></div> : null}

      <div className="simulator-metrics" aria-label="Cluster summary">
        <article className="sim-metric-card sim-metric-health">
          <span className="sim-metric-icon" aria-hidden="true">◎</span>
          <div><p>Cluster state</p><strong>{loading ? "Connecting…" : clusterState}</strong></div>
        </article>
        <article className="sim-metric-card">
          <span className="sim-metric-icon sim-icon-green" aria-hidden="true">●</span>
          <div><p>Active nodes</p><strong>{activeCount}<small> / {nodes.length}</small></strong></div>
        </article>
        <article className="sim-metric-card">
          <span className="sim-metric-icon sim-icon-red" aria-hidden="true">●</span>
          <div><p>Unavailable</p><strong>{failedCount}<small> node{failedCount === 1 ? "" : "s"}</small></strong></div>
        </article>
        <article className="sim-metric-card">
          <span className="sim-metric-icon sim-icon-amber" aria-hidden="true">◷</span>
          <div><p>Last refreshed</p><strong>{lastUpdated ? lastUpdated.toLocaleTimeString() : "—"}</strong></div>
        </article>
      </div>

      <div className="simulator-layout">
        <div className="simulator-main-column">
          <article className="simulator-panel topology-panel">
            <div className="sim-panel-heading">
              <div><p className="eyebrow">Live topology</p><h3>Cluster map</h3></div>
              <span className="sim-live-label"><i /> Auto refresh · 5 sec</span>
            </div>
            {loading && nodes.length === 0 ? (
              <div className="sim-empty-state">Connecting to the master service…</div>
            ) : nodes.length === 0 ? (
              <div className="sim-empty-state">No storage nodes have registered yet. Check the master and node services.</div>
            ) : (
              <div className="sim-topology">
                <div className="sim-master-node">
                  <span className="sim-node-symbol" aria-hidden="true">M</span>
                  <div><strong>Master</strong><span>Metadata coordinator</span></div>
                  <span className="sim-master-status"><i /> ONLINE</span>
                </div>
                <div className="sim-link-rail" aria-hidden="true"><span /><span /><span /></div>
                <div className="sim-topology-nodes">
                  {nodes.map((node) => {
                    const isOffline = Boolean(simulatedOffline[node.nodeId]) || node.status === "FAILED";
                    const isStale = node.status === "STALE";
                    const isUnknown = node.status === "UNKNOWN" || isStale;
                    return (
                      <div className={`sim-topology-node ${isOffline ? "is-offline" : isUnknown ? "is-unknown" : "is-online"}`} key={node.nodeId}>
                        <span className="sim-storage-glyph" aria-hidden="true">▤</span>
                        <strong>{node.nodeId}</strong>
                        <span>{isOffline ? "Connection interrupted" : isStale ? "Heartbeat delayed" : isUnknown ? "Awaiting heartbeat" : "Heartbeat active"}</span>
                      </div>
                    );
                  })}
                </div>
                <div className="sim-topology-legend"><span><i className="legend-online" /> Online</span><span><i className="legend-offline" /> Offline</span><span><i className="legend-unknown" /> Unknown / stale</span></div>
              </div>
            )}
          </article>

          <article className="simulator-panel node-control-panel">
            <div className="sim-panel-heading">
              <div><p className="eyebrow">Failure controls</p><h3>Storage nodes <span className="sim-count">{nodes.length}</span></h3></div>
              <p className="sim-panel-hint">Actions affect the selected node only.</p>
            </div>
            {nodes.length > 0 ? (
              <div className="sim-node-grid">
                {nodes.map((node) => {
                  const simulated = Boolean(simulatedOffline[node.nodeId]);
                  const failed = simulated || node.status === "FAILED";
                  const unknown = (node.status === "UNKNOWN" || node.status === "STALE") && !simulated;
                  const stateLabel = simulated ? "SIMULATED OFFLINE" : node.status;
                  const stateClass = failed ? "offline" : unknown ? "unknown" : "online";
                  const actionDisabled = pendingNode === node.nodeId || (unknown && !simulated);
                  return (
                    <article className={`sim-node-card ${failed ? "card-offline" : ""}`} key={node.nodeId}>
                      <div className="sim-node-card-top">
                        <span className={`sim-node-avatar ${stateClass}`} aria-hidden="true">▤</span>
                        <span className={`sim-state-badge ${stateClass}`}><i />{stateLabel}</span>
                      </div>
                      <h4>{node.nodeId}</h4>
                      <p className="sim-node-address" title={node.nodeUrl}>{node.nodeUrl}</p>
                      <div className="sim-heartbeat"><span>Last heartbeat</span><strong>{formatHeartbeat(node.lastHeartbeat)}</strong></div>
                      <button
                        className={`sim-action-button ${failed ? "restore" : "take-offline"}`}
                        type="button"
                        onClick={() => changeNodeState(node)}
                        disabled={actionDisabled}
                      >
                        {pendingNode === node.nodeId ? "Sending command…" : failed ? "Restore node" : unknown ? "Waiting for heartbeat" : "Take offline"}
                      </button>
                    </article>
                  );
                })}
              </div>
            ) : !loading ? <div className="sim-empty-state">No nodes are available to control.</div> : null}
            <p className="sim-safety-note"><span aria-hidden="true">ⓘ</span> Node actions are simulated through the master. Recovery and heartbeat updates may take several seconds.</p>
          </article>
        </div>

        <aside className="simulator-panel sim-event-panel">
          <div className="sim-panel-heading">
            <div><p className="eyebrow">Cluster activity</p><h3>Event timeline</h3></div>
            <span className="sim-event-count">{events.length}</span>
          </div>
          {events.length === 0 ? (
            <div className="sim-empty-state sim-events-empty">Events will appear here when nodes send heartbeats or the system performs repair work.</div>
          ) : (
            <ol className="sim-event-list">
              {events.map((event, index) => {
                const component = String(event.component || "SYSTEM").toLowerCase();
                const tone = event.level === "ERROR" ? "error" : event.level === "WARN" ? "warning" : "info";
                return (
                  <li className={`sim-event-item event-${tone}`} key={`${event.timestamp}-${event.component}-${index}`}>
                    <span className={`sim-event-marker marker-${component}`} />
                    <div className="sim-event-meta"><span>{event.component || "SYSTEM"}</span><time>{formatEventTime(event.timestamp)}</time></div>
                    <p>{event.message}</p>
                  </li>
                );
              })}
            </ol>
          )}
          <div className="sim-event-footer">Showing heartbeat, repair, and integrity events</div>
        </aside>
      </div>
    </section>
  );
}

import React, { useState, useEffect } from "react";
import { getNodeStatus, getLogs, simulateNodeOffline, simulateNodeOnline } from "../api";

export default function SimulationPage() {
  const [nodes, setNodes] = useState([]);
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(true);

  const fetchState = async () => {
    try {
      const [nodesData, logsData] = await Promise.all([
        getNodeStatus(),
        getLogs("ALL", 100)
      ]);
      setNodes(nodesData);
      
      const filteredLogs = logsData.filter(log => 
        ["HEARTBEAT", "REPAIR", "INTEGRITY"].includes(log.component)
      );
      setLogs(filteredLogs);
    } catch (err) {
      console.error("Simulation error", err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchState();
    const intervalId = setInterval(fetchState, 1000);
    return () => clearInterval(intervalId);
  }, []);

  const handleKill = async (nodeId) => {
    try {
      await simulateNodeOffline(nodeId);
      // Optimistic update
      setNodes(prev => prev.map(n => n.nodeId === nodeId ? { ...n, status: "FAILED" } : n));
    } catch (err) {
      alert("Failed to kill node: " + err.message);
    }
  };

  const handleRevive = async (nodeId) => {
    try {
      await simulateNodeOnline(nodeId);
      // Optimistic update
      setNodes(prev => prev.map(n => n.nodeId === nodeId ? { ...n, status: "ACTIVE" } : n));
    } catch (err) {
      alert("Failed to revive node: " + err.message);
    }
  };

  if (loading) return <div className="p-8">Loading simulation...</div>;

  return (
    <div className="p-8 max-w-7xl mx-auto space-y-8 animate-fade-in">
      <div>
        <h1 className="text-3xl font-bold mb-2">Chaos Simulator</h1>
        <p className="text-slate-500">Kill nodes to observe real-time failure detection and re-replication.</p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
        {/* Node Control Panel */}
        <div className="space-y-4">
          <h2 className="text-xl font-semibold">Node Cluster</h2>
          <div className="grid gap-4">
            {nodes.map(node => (
              <div key={node.nodeId} className="bg-white border border-slate-200 rounded-xl p-6 shadow-sm flex items-center justify-between">
                <div>
                  <div className="flex items-center gap-2 mb-1">
                    <h3 className="font-bold text-lg">{node.nodeId}</h3>
                    <span className={`status-pill ${node.status === 'ACTIVE' ? 'status-active' : 'status-failed'}`}>
                      {node.status}
                    </span>
                  </div>
                  <p className="text-sm text-slate-500 font-mono">{node.nodeUrl}</p>
                </div>
                <div>
                  {node.status === "ACTIVE" ? (
                    <button 
                      onClick={() => handleKill(node.nodeId)}
                      className="bg-red-50 text-red-600 hover:bg-red-100 px-4 py-2 rounded-lg font-medium transition-colors"
                    >
                      KILL NODE
                    </button>
                  ) : (
                    <button 
                      onClick={() => handleRevive(node.nodeId)}
                      className="bg-green-50 text-green-700 hover:bg-green-100 px-4 py-2 rounded-lg font-medium transition-colors"
                    >
                      REVIVE
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Live Terminal */}
        <div className="space-y-4">
          <h2 className="text-xl font-semibold">Live Master Logs</h2>
          <div className="bg-slate-900 rounded-xl p-4 overflow-y-auto h-[600px] shadow-inner font-mono text-sm">
            {logs.length === 0 ? (
              <div className="text-slate-500">Waiting for events...</div>
            ) : (
              logs.map((log, i) => (
                <div key={i} className="mb-2 break-all">
                  <span className="text-slate-500 mr-2">[{new Date(log.timestamp).toLocaleTimeString()}]</span>
                  <span className={`mr-2 font-bold ${
                    log.level === 'INFO' ? 'text-blue-400' :
                    log.level === 'WARN' ? 'text-yellow-400' :
                    'text-red-400'
                  }`}>
                    {log.level}
                  </span>
                  <span className="text-purple-400 mr-2">[{log.component}]</span>
                  <span className="text-slate-300">{log.message}</span>
                </div>
              ))
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

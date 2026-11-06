const BASE_URL = (import.meta.env.VITE_API_BASE_URL || "http://localhost:8080").replace(/\/$/, "");
const USE_MOCK_API = String(import.meta.env.VITE_USE_MOCK_API || "false").toLowerCase() === "true";

const ENDPOINTS = {
  upload: import.meta.env.VITE_API_UPLOAD_PATH || "/upload",
  download: import.meta.env.VITE_API_DOWNLOAD_PATH || "/download",
  files: import.meta.env.VITE_API_FILES_PATH || "/files",
  nodeStatus: import.meta.env.VITE_API_NODE_STATUS_PATH || "/nodes/status",
  systemHealth: import.meta.env.VITE_API_SYSTEM_HEALTH_PATH || "/system/health",
  logs: import.meta.env.VITE_API_LOGS_PATH || "/logs",
  masterLogs: import.meta.env.VITE_API_MASTER_LOGS_PATH || "/logs/master"
};

const MOCK_DELAY_MS = Number(import.meta.env.VITE_MOCK_DELAY_MS || 250);

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function createError(message, statusCode) {
  const error = new Error(message);
  error.statusCode = statusCode;
  return error;
}

async function parseError(response) {
  let text = "";
  try {
    text = await response.text();
  } catch {
    text = "";
  }

  // The master now returns JSON errors like { status, error, message, path }; show just the message.
  let message = text;
  try {
    const parsed = JSON.parse(text);
    if (parsed && typeof parsed.message === "string" && parsed.message) {
      message = parsed.message;
    }
  } catch {
    // Not JSON (plain text or empty): keep the raw text.
  }

  return createError(message || `Request failed (${response.status})`, response.status);
}

async function requestJson(path, options = {}) {
  const response = await fetch(`${BASE_URL}${path}`, options);
  if (!response.ok) throw await parseError(response);
  const contentType = response.headers.get("content-type") || "";
  if (contentType.includes("application/json")) {
    return response.json();
  }

  const text = await response.text();
  return text;
}

async function requestBlob(path, options = {}) {
  const response = await fetch(`${BASE_URL}${path}`, options);
  if (!response.ok) throw await parseError(response);
  const blob = await response.blob();
  const contentDisposition = response.headers.get("content-disposition") || "";
  const contentType = response.headers.get("content-type") || "";
  return {
    blob,
    contentDisposition,
    contentType
  };
}

async function requestText(path, options = {}) {
  const response = await fetch(`${BASE_URL}${path}`, options);
  if (!response.ok) throw await parseError(response);
  return response.text();
}

function normalizeUploadResponse(raw) {
  if (!raw) throw createError("Upload response is empty");

  if (typeof raw === "string") return { fileId: raw };

  if (typeof raw.fileId === "string" && raw.fileId) return { fileId: raw.fileId };

  throw createError("Upload response does not contain fileId");
}

const mockData = {
  upload: { fileId: "mock-file-001" },
  files: [
    { fileId: "123e4567-e89b-12d3-a456-426614174000", filename: "report.pdf", chunkCount: 3, minReplicas: 2, targetReplicas: 2 },
    { fileId: "123e4567-e89b-12d3-a456-426614174001", filename: "notes.txt", chunkCount: 1, minReplicas: 1, targetReplicas: 2 }
  ],
  nodeStatus: [
    { nodeId: "node-1", status: "ACTIVE", lastHeartbeat: Date.now(), nodeUrl: "http://localhost:5001" },
    { nodeId: "node-2", status: "ACTIVE", lastHeartbeat: Date.now(), nodeUrl: "http://localhost:5002" },
    { nodeId: "node-3", status: "FAILED", lastHeartbeat: Date.now() - 30000, nodeUrl: "http://localhost:5003" },
    { nodeId: "node-4", status: "ACTIVE", lastHeartbeat: Date.now(), nodeUrl: "http://localhost:5004" },
    { nodeId: "node-5", status: "STALE", lastHeartbeat: Date.now() - 20000, nodeUrl: "http://localhost:5005" }
  ],
  systemHealth: {
    status: "DEGRADED",
    activeNodes: 2,
    staleNodes: 1,
    failedNodes: 1
  }
};

export async function uploadFile(file) {
  if (!file) throw createError("File is required for upload");

  if (USE_MOCK_API) {
    await sleep(MOCK_DELAY_MS);
    return mockData.upload;
  }

  const formData = new FormData();
  formData.append("file", file);
  const raw = await requestJson(ENDPOINTS.upload, { method: "POST", body: formData });
  return normalizeUploadResponse(raw);
}

export async function downloadFile(fileId) {
  if (!fileId) throw createError("fileId is required for download");

  if (USE_MOCK_API) {
    await sleep(MOCK_DELAY_MS);
    return {
      blob: new Blob([`Mock file content for ${fileId}`], { type: "text/plain" }),
      contentDisposition: `attachment; filename="${fileId}.txt"`,
      contentType: "text/plain"
    };
  }

  return requestBlob(`${ENDPOINTS.download}/${encodeURIComponent(fileId)}`);
}

export async function listFiles() {
  if (USE_MOCK_API) {
    await sleep(MOCK_DELAY_MS);
    return mockData.files;
  }

  const raw = await requestJson(ENDPOINTS.files);
  return Array.isArray(raw) ? raw : [];
}

export async function getFileDetails(fileId) {
  if (!fileId) throw createError("fileId is required");

  if (USE_MOCK_API) {
    await sleep(MOCK_DELAY_MS);
    const summary = mockData.files.find((item) => item.fileId === fileId) || mockData.files[0];
    return {
      ...summary,
      chunks: Array.from({ length: summary.chunkCount }, (_, index) => ({
        chunkId: `mock-chunk-${index}`,
        chunkIndex: index,
        checksum: "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
        nodeUrls: ["http://localhost:5001", "http://localhost:5002"].slice(0, summary.minReplicas)
      }))
    };
  }

  return requestJson(`${ENDPOINTS.files}/${encodeURIComponent(fileId)}`);
}

export async function deleteFile(fileId) {
  if (!fileId) throw createError("fileId is required for delete");

  if (USE_MOCK_API) {
    await sleep(MOCK_DELAY_MS);
    mockData.files = mockData.files.filter((item) => item.fileId !== fileId);
    return;
  }

  await requestText(`${ENDPOINTS.files}/${encodeURIComponent(fileId)}`, { method: "DELETE" });
}

export async function getNodeStatus() {
  if (USE_MOCK_API) {
    await sleep(MOCK_DELAY_MS);
    return mockData.nodeStatus;
  }

  return requestJson(ENDPOINTS.nodeStatus);
}

export async function getSystemHealth() {
  if (USE_MOCK_API) {
    await sleep(MOCK_DELAY_MS);
    return mockData.systemHealth;
  }

  return requestJson(ENDPOINTS.systemHealth);
}

export async function getLogs(level = "ALL", limit = 100) {
  if (USE_MOCK_API) {
    await sleep(MOCK_DELAY_MS);
    return [
      { timestamp: new Date().toISOString(), level: "INFO", component: "UPLOAD", message: "Upload complete fileId=mock-file-001 chunks=1" },
      { timestamp: new Date().toISOString(), level: "WARN", component: "HEARTBEAT", message: "Node FAILED: node-3" }
    ];
  }

  const params = new URLSearchParams({
    level,
    limit: String(limit)
  });
  return requestJson(`${ENDPOINTS.logs}?${params.toString()}`);
}

export async function getMasterLogs(lines = 500) {
  if (USE_MOCK_API) {
    await sleep(MOCK_DELAY_MS);
    return [
      "2026-07-28T09:00:00.000+05:30  INFO 12345 --- [master-service] Started MasterServiceApplication",
      "2026-07-28T09:00:05.000+05:30  INFO 12345 --- [master-service] Heartbeat received from node-1 (http://localhost:5001)",
      "2026-07-28T09:00:10.000+05:30  WARN 12345 --- [master-service] Node FAILED: node-3"
    ].join("\n");
  }

  const params = new URLSearchParams({
    lines: String(lines)
  });
  return requestText(`${ENDPOINTS.masterLogs}?${params.toString()}`);
}

export async function simulateNodeOffline(nodeId) {
  if (USE_MOCK_API) {
    await sleep(MOCK_DELAY_MS);
    return;
  }
  return requestText(`/api/simulation/nodes/${nodeId}/offline`, { method: "POST" });
}

export async function simulateNodeOnline(nodeId) {
  if (USE_MOCK_API) {
    await sleep(MOCK_DELAY_MS);
    return;
  }
  return requestText(`/api/simulation/nodes/${nodeId}/online`, { method: "POST" });
}

export const apiConfig = {
  baseUrl: BASE_URL,
  useMockApi: USE_MOCK_API,
  endpoints: ENDPOINTS
};

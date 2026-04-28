const BASE_URL = (import.meta.env.VITE_API_BASE_URL || "http://localhost:8080").replace(/\/$/, "");
const USE_MOCK_API = String(import.meta.env.VITE_USE_MOCK_API || "false").toLowerCase() === "true";

const ENDPOINTS = {
  upload: import.meta.env.VITE_API_UPLOAD_PATH || "/upload",
  download: import.meta.env.VITE_API_DOWNLOAD_PATH || "/download",
  nodeStatus: import.meta.env.VITE_API_NODE_STATUS_PATH || "/nodes/status",
  systemHealth: import.meta.env.VITE_API_SYSTEM_HEALTH_PATH || "/system/health",
  logs: import.meta.env.VITE_API_LOGS_PATH || "/logs"
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

  const message = text || `Request failed (${response.status})`;
  return createError(message, response.status);
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

function normalizeUploadResponse(raw) {
  if (!raw) throw createError("Upload response is empty");

  if (typeof raw === "string") return { fileId: raw };

  if (typeof raw.fileId === "string" && raw.fileId) return { fileId: raw.fileId };

  throw createError("Upload response does not contain fileId");
}

const mockData = {
  upload: { fileId: "mock-file-001" },
  nodeStatus: [
    { nodeId: "node-1", status: "ACTIVE", lastHeartbeat: Date.now(), nodeUrl: "http://localhost:5001" },
    { nodeId: "node-2", status: "ACTIVE", lastHeartbeat: Date.now(), nodeUrl: "http://localhost:5002" },
    { nodeId: "node-3", status: "FAILED", lastHeartbeat: Date.now() - 30000, nodeUrl: "http://localhost:5003" }
  ],
  systemHealth: {
    status: "DEGRADED",
    activeNodes: 2,
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

export const apiConfig = {
  baseUrl: BASE_URL,
  useMockApi: USE_MOCK_API,
  endpoints: ENDPOINTS
};

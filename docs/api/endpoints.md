# API Endpoints Reference

## Base URLs

- Master service: `http://localhost:8080`
- Storage nodes: `http://localhost:5001` to `http://localhost:5005` (in Docker Compose the nodes are only reachable inside the Docker network; their ports are not published)

All examples use `curl`. In Windows PowerShell, use `curl.exe` instead, because plain `curl` is an alias for another command there.

---

## Authentication

| Who calls | Which endpoints | How |
| --- | --- | --- |
| A user or the dashboard | Every master endpoint except `POST /heartbeat` | HTTP Basic: `curl -u USERNAME:PASSWORD ...` (`APP_USERNAME` default `admin`, and `APP_PASSWORD`) |
| A storage node | `POST /heartbeat` on the master | Header `X-Node-Token: <NODE_TOKEN>` |
| The master | Every storage node endpoint | Header `X-Node-Token: <NODE_TOKEN>` |

- A missing or wrong credential gives `401` with an empty body. The response deliberately has no `WWW-Authenticate` header, so browsers do not open their own login dialog.
- The user login does not work on `/heartbeat`, and the node token does not work on the user endpoints.
- `NODE_TOKEN` must be at least 16 characters and identical on the master and every node.

## Error format

Errors raised by the application are JSON:

```json
{
  "timestamp": "2026-11-21T06:15:00.123Z",
  "status": 404,
  "error": "Not Found",
  "message": "File not found: 123e4567-e89b-12d3-a456-426614174000",
  "path": "/download/123e4567-e89b-12d3-a456-426614174000"
}
```

| Status | When |
| --- | --- |
| `400` | Empty upload, missing `file` part, invalid multipart request, filename over 255 characters, or a `fileId` that is not a UUID |
| `401` | No credential or a wrong one (empty body) |
| `404` | Unknown `fileId` |
| `413` | Upload larger than 10 MB |
| `503` | A chunk has no valid replica, too few storage nodes are available for an upload, or a storage node could not be reached |
| `500` | Anything unexpected. The message is always the generic `Internal server error.` so details are not leaked. |

---

## 1) Master Service Endpoints

### 1.1 Upload File

- **Method:** `POST`
- **URL:** `/upload`
- **Content-Type:** `multipart/form-data`
- **Body fields:** `file` (File)

The master splits the file into chunks (`STORAGE_CHUNK_SIZE_BYTES`, default 1 MiB), stores each chunk on `STORAGE_REPLICATION_FACTOR` different `ACTIVE` nodes (default 2), and records the metadata. Only the base name of the uploaded filename is kept.

**Success:** `200`, body is the plain-text `fileId` (a UUID).

```bash
curl -u admin:YOUR_PASSWORD -F "file=@/path/to/file.pdf" http://localhost:8080/upload
```

---

### 1.2 Download File

- **Method:** `GET`
- **URL:** `/download/{fileId}`

The master fetches every chunk, verifies its SHA-256 checksum, falls back to another replica if a copy is missing or corrupt, and returns the reassembled file.

**Success:** `200`, body is the raw file bytes.

Headers:
- `Content-Disposition: attachment; filename="..."; filename*=UTF-8''...` with the original filename
- `Content-Type`: guessed from the filename (for example `application/pdf`), otherwise `application/octet-stream`

**Errors:** `400` (not a UUID), `404` (unknown file), `503` (a chunk has no valid replica).

```bash
curl -u admin:YOUR_PASSWORD -L "http://localhost:8080/download/<fileId>" -o downloaded_file
```

---

### 1.3 List Files

- **Method:** `GET`
- **URL:** `/files`

**Success:** `200`, JSON array sorted by filename (case-insensitive).

```json
[
  {
    "fileId": "123e4567-e89b-12d3-a456-426614174000",
    "filename": "report.pdf",
    "chunkCount": 3,
    "minReplicas": 2,
    "targetReplicas": 2
  }
]
```

`minReplicas` is the lowest number of copies across the file's chunks. A value below `targetReplicas` means the file is under-replicated, and `0` means at least one chunk has no copies.

---

### 1.4 File Details

- **Method:** `GET`
- **URL:** `/files/{fileId}`

**Success:** `200`

```json
{
  "fileId": "123e4567-e89b-12d3-a456-426614174000",
  "filename": "report.pdf",
  "chunkCount": 1,
  "targetReplicas": 2,
  "chunks": [
    {
      "chunkId": "0b7c2d1e-5f4a-4c6e-9d3b-1a2b3c4d5e6f",
      "chunkIndex": 0,
      "checksum": "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
      "nodeUrls": ["http://localhost:5001", "http://localhost:5002"]
    }
  ]
}
```

Chunks are ordered by `chunkIndex`. **Errors:** `400`, `404`.

---

### 1.5 Delete File

- **Method:** `DELETE`
- **URL:** `/files/{fileId}`

Deletes the metadata first, then asks each node to remove its chunk files after the change is committed. Removing chunks from nodes is best effort: a node that is down keeps its copy.

**Success:** `204`, no body. **Errors:** `400`, `404`.

---

### 1.6 Heartbeat (Storage Node to Master)

- **Method:** `POST`
- **URL:** `/heartbeat`
- **Auth:** `X-Node-Token` header (not the user login)
- **Content-Type:** `application/json`

```json
{
  "nodeId": "node-1",
  "nodeUrl": "http://localhost:5001"
}
```

**Success:** `200`, body `Heartbeat received`.

**Errors:**
- missing or blank `nodeId`: `400`, body `nodeId is required`
- missing or blank `nodeUrl`: `400`, body `nodeUrl is required`
- missing or wrong token: `401`

```bash
curl -X POST http://localhost:8080/heartbeat \
  -H "Content-Type: application/json" -H "X-Node-Token: YOUR_NODE_TOKEN" \
  -d '{"nodeId":"node-1","nodeUrl":"http://localhost:5001"}'
```

---

### 1.7 Node Status

- **Method:** `GET`
- **URL:** `/nodes/status`

```json
[
  {
    "nodeId": "node-1",
    "nodeUrl": "http://localhost:5001",
    "lastHeartbeat": 1714300000000,
    "status": "ACTIVE"
  }
]
```

`lastHeartbeat` is milliseconds since the Unix epoch.

| `status` | Meaning |
| --- | --- |
| `ACTIVE` | Heartbeat within the stale timeout (default 15 s) |
| `STALE` | Silent longer than the stale timeout. No new uploads; its replica records are kept. |
| `FAILED` | Silent longer than the dead timeout (default 60 s). Its replicas are dropped and re-replicated. |
| `UNKNOWN` | Known, but in none of the three sets |

---

### 1.8 System Health

- **Method:** `GET`
- **URL:** `/system/health`

```json
{
  "status": "DEGRADED",
  "activeNodes": 3,
  "staleNodes": 1,
  "failedNodes": 1,
  "activeNodeIds": ["node-1", "node-2", "node-3"],
  "staleNodeIds": ["node-4"],
  "failedNodeIds": ["node-5"],
  "staleAfterSeconds": 15,
  "failedAfterSeconds": 60
}
```

`status` logic:
- `UNKNOWN`: no node has reported yet
- `DEGRADED`: at least one node is `STALE` or `FAILED`
- `HEALTHY`: otherwise

---

### 1.9 Event Logs

- **Method:** `GET`
- **URL:** `/logs`
- **Query:** `level` (`ALL`, `INFO`, `WARN`, `ERROR`; default `ALL`) and `limit` (default `100`)

```json
[
  {
    "timestamp": "2026-11-21T06:15:00.000Z",
    "level": "WARN",
    "component": "HEARTBEAT",
    "message": "Node FAILED: node-2 (no heartbeat for 61s). Dropping its replicas and re-replicating."
  }
]
```

Components include `UPLOAD`, `DOWNLOAD`, `DELETE`, `HEARTBEAT`, `REPAIR` and `INTEGRITY`. Events are kept in memory only.

---

### 1.10 Master Log File Tail

- **Method:** `GET`
- **URL:** `/logs/master`
- **Query:** `lines` (default `500`)

**Success:** `200`, `text/plain`, the last lines of `logs/master-service.log`.

---

### 1.11 Simulate a Node Offline or Online

- **Method:** `POST`
- **URL:** `/api/simulation/nodes/{nodeId}/offline` or `/api/simulation/nodes/{nodeId}/online`

The master looks up the node's URL from its last heartbeat and calls the node's `/simulate/offline` or `/simulate/online`.

**Success:** `200`, body `Simulating node offline` or `Simulating node online`.
**Errors:** `404` (`Node not found`, the node never sent a heartbeat), `500` (`Error talking to node`).

---

## 2) Storage Node Endpoints

These are internal cluster endpoints. Every one needs the `X-Node-Token` header and returns `401` without it. While a node is simulating offline, the chunk endpoints return `503`.

### 2.1 Store Chunk

- **Method:** `POST`
- **URL:** `/storeChunk`
- **Content-Type:** `multipart/form-data`
- **Body fields:** `chunkId` (text), `file` (the chunk bytes)

**Success:** `200`, body `Stored`. **Errors:** `503` (offline), `500` (`Error storing chunk`).

### 2.2 Get Chunk

- **Method:** `GET`
- **URL:** `/getChunk/{chunkId}`

**Success:** `200`, raw chunk bytes. **Errors:** `404` (not stored here), `503` (offline).

### 2.3 List Chunks

- **Method:** `GET`
- **URL:** `/chunks`

Returns the ids of every chunk stored on this node, sorted. The master uses it to reconcile a node that comes back after being `FAILED`.

**Success:** `200`, for example `["0b7c...", "4f1a..."]`. **Errors:** `503` (offline), `500`.

### 2.4 Delete Chunk

- **Method:** `DELETE`
- **URL:** `/deleteChunk/{chunkId}`

**Success:** `200`, body `Deleted` (also when the chunk did not exist). **Errors:** `503` (offline), `500` (`Error deleting chunk`).

### 2.5 Simulate Offline / Online

- **Method:** `POST`
- **URL:** `/simulate/offline` or `/simulate/online`

Offline mode stops the node's heartbeats and makes its chunk endpoints answer `503`. The process keeps running and no data is lost.

**Success:** `200`, body `Node is now simulated offline` or `Node is now simulated online`.

---

## 3) Frontend Environment to API Mapping

From `client-ui/.env` (all optional; the defaults match the master's routes):

| Variable | Purpose |
| --- | --- |
| `VITE_API_BASE_URL` | Master base URL (default `http://localhost:8080`) |
| `VITE_API_UPLOAD_PATH` | Upload path (default `/upload`) |
| `VITE_API_DOWNLOAD_PATH` | Download path (default `/download`) |
| `VITE_API_FILES_PATH` | File list path (default `/files`) |
| `VITE_API_NODE_STATUS_PATH` | Node status path (default `/nodes/status`) |
| `VITE_API_SYSTEM_HEALTH_PATH` | Health path (default `/system/health`) |
| `VITE_API_LOGS_PATH` | Event logs path (default `/logs`) |
| `VITE_API_MASTER_LOGS_PATH` | Master log tail path (default `/logs/master`) |
| `VITE_MAX_UPLOAD_MB` | Largest file the upload page accepts before sending |
| `VITE_USE_MOCK_API` | `true` uses mock responses and needs no master |
| `VITE_MOCK_DELAY_MS` | Delay of the mock responses |

The dashboard shows its own sign-in page, keeps the login header in the browser tab's `sessionStorage`, and sends it on every request. A `401` sends the user back to the sign-in page.

---

## 4) CORS Note

The master allows browser calls only from `http://localhost:5173` (methods `GET`, `POST`, `PUT`, `DELETE`, `OPTIONS`, any request header; `Content-Disposition` and `Content-Type` are exposed). If the dashboard runs on another host or port, change the CORS setting in the master's `AppConfig`.

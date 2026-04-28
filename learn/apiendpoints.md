# API Endpoints Reference

## Base URLs

- Master service: `http://localhost:8080`
- Storage node 1: `http://localhost:5001`
- Storage node 2: `http://localhost:5002`
- Storage node 3: `http://localhost:5003`

---

## 1) Master Service Endpoints

### 1.1 Upload File

- **Method:** `POST`
- **URL:** `/upload`
- **Content-Type:** `multipart/form-data`
- **Body fields:**
  - `file` (File)

#### Success Response

- **Status:** `200`
- **Body:** plain text `fileId` (UUID string)

#### Example cURL

```bash
curl -X POST "http://localhost:8080/upload" \
  -F "file=@/path/to/file.pdf"
```

---

### 1.2 Download File

- **Method:** `GET`
- **URL:** `/download/{fileId}`

#### Path Params

- `fileId` - file identifier returned from upload

#### Success Response

- **Status:** `200`
- **Body:** raw file bytes
- **Headers (current):**
  - `Content-Disposition: attachment; filename=output.pdf`
  - `Content-Type: application/octet-stream`

#### Example cURL

```bash
curl -L "http://localhost:8080/download/<fileId>" -o downloaded_file
```

---

### 1.3 Heartbeat (Storage Node -> Master)

- **Method:** `POST`
- **URL:** `/heartbeat`
- **Content-Type:** `application/json`

#### Request Body

```json
{
  "nodeId": "node-1",
  "nodeUrl": "http://localhost:5001"
}
```

#### Success Response

- **Status:** `200`
- **Body:** `"Heartbeat received"`

#### Validation Errors

- missing `nodeId` -> `400` with `"nodeId is required"`
- missing `nodeUrl` -> `400` with `"nodeUrl is required"`

---

### 1.4 Node Status

- **Method:** `GET`
- **URL:** `/nodes/status`

#### Success Response

- **Status:** `200`
- **Body:** JSON array

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

`status` values:
- `ACTIVE`
- `FAILED`
- `UNKNOWN`

---

### 1.5 System Health

- **Method:** `GET`
- **URL:** `/system/health`

#### Success Response

- **Status:** `200`
- **Body:** JSON object

```json
{
  "status": "DEGRADED",
  "activeNodes": 2,
  "failedNodes": 1,
  "activeNodeIds": ["node-1", "node-3"],
  "failedNodeIds": ["node-2"]
}
```

`status` logic:
- `HEALTHY`: no failed nodes
- `DEGRADED`: one or more failed nodes
- `UNKNOWN`: no active/failed nodes yet

---

### 1.6 Logs

- **Method:** `GET`
- **URL:** `/logs`

#### Query Params

- `level` (optional): `ALL | INFO | WARN | ERROR`
- `limit` (optional): max number of rows (default `100`)

#### Example

`/logs?level=WARN&limit=50`

#### Success Response

- **Status:** `200`
- **Body:** JSON array

```json
[
  {
    "timestamp": "2026-04-28T14:35:58.373Z",
    "level": "WARN",
    "component": "HEARTBEAT",
    "message": "Node FAILED: node-2"
  }
]
```

---

## 2) Storage Node Endpoints

### 2.1 Store Chunk

- **Method:** `POST`
- **URL:** `/storeChunk`
- **Content-Type:** `multipart/form-data`

#### Body fields

- `chunkId` (Text)
- `file` (File bytes for chunk)

#### Success Response

- **Status:** `200`
- **Body:** `"Stored"`

---

### 2.2 Get Chunk

- **Method:** `GET`
- **URL:** `/getChunk/{chunkId}`

#### Path Params

- `chunkId` - chunk identifier

#### Success Response

- **Status:** `200`
- **Body:** raw chunk bytes

---

## 3) Frontend Env -> API Mapping

From `client-ui/.env`:

- `VITE_API_BASE_URL` -> master base URL
- `VITE_API_UPLOAD_PATH` -> upload path
- `VITE_API_DOWNLOAD_PATH` -> download path
- `VITE_API_NODE_STATUS_PATH` -> node status path
- `VITE_API_SYSTEM_HEALTH_PATH` -> health path
- `VITE_API_LOGS_PATH` -> logs path
- `VITE_USE_MOCK_API` -> use mock responses (`true/false`)

---

## 4) CORS Note

Master service CORS currently allows frontend origin:

- `http://localhost:5173`

If frontend runs on another port/host, update backend CORS config.

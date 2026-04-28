# Staged Changes - 28-04-2026

## Date

28 April 2026

## Summary

Today we completed frontend Workstreams **B, C, D** and extended backend APIs required for monitoring and logs.

Current result:
- Upload and download are working from UI.
- Dashboard and Node Status now consume live backend data.
- Logs page now works with real backend `/logs` API.

---

## 1) Frontend Workstream B (API Layer) - Completed

### What was implemented

- Added centralized frontend API module:
  - `client-ui/src/api/index.js`
- Added minimal shared type documentation:
  - `client-ui/src/types.js`
- Added environment config:
  - `client-ui/.env`

### API functions added

- `uploadFile(file)`
- `downloadFile(fileId)`
- `getNodeStatus()`
- `getSystemHealth()`
- `getLogs(level, limit)` (added later for Logs page)

### Important handling added

- Supports both JSON and plain-text API responses (upload fix).
- Supports Blob download with response headers.
- Optional mock switch:
  - `VITE_USE_MOCK_API=true|false`

---

## 2) Frontend Workstream C (Upload/Files UI) - Completed

### What was implemented

- `UploadPage` wired to backend upload API.
- `FilesPage` wired to backend download API.
- Added upload/download success + error states.
- Added local storage based recent uploads list.

### Files changed

- `client-ui/src/pages/UploadPage.jsx`
- `client-ui/src/pages/FilesPage.jsx`
- `client-ui/src/utils/recentFiles.js`
- `client-ui/src/styles.css`

### Download filename/format fix

Issue observed:
- downloaded file was saved without extension (generic file type).

Fix implemented:
- Download name now resolved in this order:
  1. recent uploaded filename
  2. backend `Content-Disposition` filename
  3. fallback based on `Content-Type` extension

---

## 3) Frontend Workstream D (Dashboard + Node Status) - Completed

### What was implemented

- Dashboard page:
  - cluster status
  - active/failed node counts
  - manual refresh
  - auto refresh (10s)
- Node Status page:
  - node id
  - status
  - last heartbeat
  - node URL
  - manual refresh
  - auto refresh (10s)

### Files changed

- `client-ui/src/pages/DashboardPage.jsx`
- `client-ui/src/pages/NodeStatusPage.jsx`
- `client-ui/src/styles.css`

### Backend dependency resolved

Frontend initially failed with 404 on `/nodes/status`.
Backend endpoints were then added (see backend section below).

---

## 4) Logs Section (UI + Backend) - Completed

### Backend implemented

- In-memory event logging service.
- Logs retrieval API endpoint.

### Frontend implemented

- Logs page with:
  - level filter (`ALL`, `INFO`, `WARN`, `ERROR`)
  - manual refresh
  - auto refresh (5s)
  - table: timestamp, level, component, message

### Files changed (frontend)

- `client-ui/src/pages/LogsPage.jsx`
- `client-ui/src/api/index.js`
- `client-ui/src/styles.css`
- `client-ui/.env` (`VITE_API_LOGS_PATH=/logs`)

---

## 5) Backend API Additions/Changes (master-service)

### CORS

- Added global CORS config allowing frontend origin:
  - `http://localhost:5173`
- File:
  - `master-service/src/main/java/com/byteharvest/master/config/AppConfig.java`

### Monitoring APIs added

- `GET /nodes/status`
- `GET /system/health`
- File:
  - `master-service/src/main/java/com/byteharvest/master/controller/MonitoringController.java`

### Logs API added

- `GET /logs?level=ALL|INFO|WARN|ERROR&limit=<n>`
- File:
  - `master-service/src/main/java/com/byteharvest/master/controller/LogsController.java`

### Event source instrumentation added

- Upload success/failure logs
- Download success/failure logs
- Heartbeat node failed/recovered logs
- Replication repair summary logs

Files updated:
- `master-service/src/main/java/com/byteharvest/master/service/ChunkService.java`
- `master-service/src/main/java/com/byteharvest/master/service/HeartbeatService.java`
- `master-service/src/main/java/com/byteharvest/master/service/ReplicationRepairService.java`
- `master-service/src/main/java/com/byteharvest/master/service/EventLogService.java` (new)

---

## 6) Technical Flow (Current End-to-End)

### Upload flow

1. User uploads file from UI (`/upload` page).
2. Frontend calls `POST /upload` on master.
3. Master splits file into chunks.
4. Chunks are replicated to storage nodes (`RF=2`).
5. Master returns `fileId`.
6. UI stores recent upload metadata in local storage.

### Download flow

1. User enters/selects `fileId` in `/files`.
2. Frontend calls `GET /download/{fileId}`.
3. Master fetches chunk replicas and reconstructs file.
4. UI receives Blob and triggers browser download.
5. Filename preserved via metadata/header fallback logic.

### Monitoring flow

1. Storage nodes send heartbeat to master.
2. Master tracks active/failed nodes.
3. UI dashboard polls:
  - `GET /system/health`
  - `GET /nodes/status`
4. UI updates cards/table every 10 seconds.

### Logs flow

1. Backend services publish events to in-memory log buffer.
2. UI logs page polls `GET /logs`.
3. User filters by level and reviews operational events.

---

## 7) Runtime Verification Status

### Verified

- Frontend build passes (`client-ui`).
- Backend build passes (`master-service`).
- Upload works from UI.
- Download works from UI.
- Dashboard and Node Status work after monitoring endpoints were added.
- Logs endpoint returns 200 and logs page fetches data.

### Notes

- Logs are in-memory; restart of master resets log history.
- For WSL runs, backend/frontend restarts are required after code/env updates.

---

## 8) API List (Current)

### Existing core APIs

- `POST /upload`
- `GET /download/{fileId}`
- `POST /heartbeat`

### Added today

- `GET /nodes/status`
- `GET /system/health`
- `GET /logs`

---

## 9) Pending / Next

- Stage 4 data integrity implementation (checksum + corruption recovery).
- Optional: persistent log storage (file/db) instead of in-memory only.

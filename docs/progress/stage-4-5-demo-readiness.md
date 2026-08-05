# Stage 4.5 - Demo Integration Readiness

## Objective

Make the project ready for a teacher-facing local demo before Docker work starts.

The priority is a stable browser flow:

1. Upload a file from React UI.
2. Download the same file from React UI.
3. Show live node health.
4. Show runtime logs.
5. Stop one storage node and show degraded-but-working behavior.

---

## Current Integration Status

- Frontend real API mode is enabled in `client-ui/.env`:
  - `VITE_USE_MOCK_API=false`
  - `VITE_API_BASE_URL=http://localhost:8080`
- Master CORS allows the Vite frontend origin:
  - `http://localhost:5173`
- Backend endpoints used by the UI are present:
  - `POST /upload`
  - `GET /download/{fileId}`
  - `GET /nodes/status`
  - `GET /system/health`
  - `GET /logs`

---

## Fixes Applied

### 1. Storage Node Path Made Demo-Safe

`storage-node/src/main/resources/application.properties` now leaves `storage.base-dir` empty by default.

Why:

- The previous value was hardcoded to a WSL path.
- Windows/IntelliJ runs could write chunks to the wrong location.
- Empty default lets the node create `storage_<port>` folders relative to the run directory.

### 2. Download Filename Preservation

The master now stores the original uploaded filename in memory and uses it in the download `Content-Disposition` header.

Why:

- Teacher demo downloads now keep recognizable filenames.
- Manual download by `fileId` no longer defaults to a misleading fixed `output.pdf`.

### 3. WSL Setup Doc Corrected

The WSL storage-node command now passes the project root as `storage.base-dir`.

Why:

- The storage-node already appends `storage_<port>` internally.
- Passing `/storage_5001` as the base would create nested paths like `storage_5001/storage_5001`.

---

## Local Demo Runbook

### 1. Start Master

```powershell
cd D:\projects\final\master-service
mvn spring-boot:run
```

### 2. Start Storage Nodes

Open three separate terminals:

```powershell
cd D:\projects\final\storage-node
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=5001"
```

```powershell
cd D:\projects\final\storage-node
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=5002"
```

```powershell
cd D:\projects\final\storage-node
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=5003"
```

### 3. Start Frontend

```powershell
cd D:\projects\final\client-ui
npm run dev
```

Open:

```text
http://localhost:5173
```

---

## Teacher Demo Flow

1. Open Dashboard and confirm cluster status becomes `HEALTHY`.
2. Open Node Status and confirm three active nodes.
3. Open Upload, upload a small file, and copy the returned `fileId`.
4. Open Files and download the uploaded file.
5. Open Logs and show upload/download/heartbeat events.
6. Stop one storage-node terminal.
7. Wait 15-25 seconds for failure detection.
8. Show Dashboard/Node Status changing to `DEGRADED`.
9. Show repair/integrity logs.
10. Download the same file again to prove replica fallback works.

---

## Known Limitations To Mention

---

## Recommended Next Stage


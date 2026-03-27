# Stage 1 Done

## Final Status

Stage 1 is complete and verified end-to-end.

| Requirement | Status |
|---|---|
| File upload API | Complete |
| Chunk splitting | Complete |
| `/storeChunk` | Complete |
| `/getChunk` | Complete |
| Round-robin distribution | Complete |
| Metadata (in-memory) | Complete |
| File distributed across nodes | Complete |
| File reconstruction | Complete |

## What Is Implemented

- Master node receives uploads and splits files into chunks.
- Chunks are distributed across storage nodes using round-robin.
- Chunk metadata is stored in memory on the master.
- Download reconstructs the original file by fetching and merging chunks in order.
- Storage is isolated per node using port-based folders:
  `storage_5001`, `storage_5002`, `storage_5003`.

## Ports

### Master Service

- `http://localhost:8080`

### Storage Nodes

- Node 1: `http://localhost:5001`
- Node 2: `http://localhost:5002`
- Node 3: `http://localhost:5003`

## API List

### 1) Upload File (Master)

- Method: `POST`
- URL: `http://localhost:8080/upload`
- Body: `form-data`
    - `file` (File): any file
- Response: `fileId` (UUID)

### 2) Download File (Master)

- Method: `GET`
- URL: `http://localhost:8080/download/{fileId}`
- Response: reconstructed file download

### 3) Store Chunk (Node)

- Method: `POST`
- URLs:
    - `http://localhost:5001/storeChunk`
    - `http://localhost:5002/storeChunk`
    - `http://localhost:5003/storeChunk`
- Body: `form-data`
    - `chunkId` (Text)
    - `file` (File)
- Response: `Stored`

### 4) Get Chunk (Node)

- Method: `GET`
- URLs:
    - `http://localhost:5001/getChunk/{chunkId}`
    - `http://localhost:5002/getChunk/{chunkId}`
    - `http://localhost:5003/getChunk/{chunkId}`
- Response: raw chunk bytes

## End-to-End Test Flow

1. `POST /upload` on master and capture returned `fileId`.
2. Verify chunks are distributed across:
    - `storage_5001/`
    - `storage_5002/`
    - `storage_5003/`
3. `GET /download/{fileId}` on master and verify file integrity.

## Viva Summary

Master splits a file into chunks, distributes chunks across storage nodes with round-robin, stores chunk metadata, and reconstructs the file on download by fetching chunks from nodes in order.

## Optional Next Improvement

- Preserve and return original filename during download (instead of fixed output name).

## Next Stage

Stage 2 focus:

- Replication (multiple chunk copies)
- Fault tolerance (node failure handling)

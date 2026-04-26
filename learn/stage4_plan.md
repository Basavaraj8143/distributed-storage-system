# Stage 4 Plan - Data Integrity (V3.5)

## Objective

Add chunk-level integrity validation so corrupted chunks are detected during read and automatically recovered from healthy replicas.

## Scope

- Generate SHA-256 checksum for each chunk during upload.
- Store checksum in master metadata per chunk.
- Validate chunk checksum during download and repair workflows.
- If a replica is corrupted, fetch from another valid replica.
- Replace corrupted replica by re-writing a clean copy to that node.

## Current Baseline (from Stage 3)

- Replication factor is implemented.
- Heartbeat-based node failure detection is implemented.
- Automatic re-replication for node failure is implemented.
- No chunk corruption detection exists yet.

## Planned Changes

### 1) Metadata Extension

- Update `ChunkMetadata` to include `checksum` field.
- Ensure checksum is persisted in in-memory metadata for each chunk.

### 2) Checksum Generation on Upload

- In master upload flow:
  - Compute SHA-256 for each chunk before sending to nodes.
  - Save checksum with chunk metadata.

### 3) Checksum Verification on Read

- In master download flow:
  - Fetch chunk bytes from replicas one by one.
  - Validate SHA-256 against expected checksum.
  - Accept first valid replica.
  - Ignore corrupted replicas and continue fallback.

### 4) Corruption Recovery

- When a corrupted replica is detected:
  - Mark that replica as invalid for that chunk.
  - Re-store the valid chunk bytes to the same node if reachable.
  - If same node write fails, repair on another active node (maintain RF).

### 5) Integrity-Aware Repair Scheduler

- Extend repair workflow to:
  - Optionally sample/verify chunk replicas periodically.
  - Replace corrupted replicas similar to under-replication repair.

## API / Contract Impact

- External APIs remain unchanged:
  - `POST /upload`
  - `GET /download/{fileId}`
  - `POST /storeChunk`
  - `GET /getChunk/{chunkId}`
- Internal metadata model changes (`checksum` added).

## Test Plan

1. Start master + 3 nodes.
2. Upload file and capture `fileId`.
3. Manually corrupt one stored chunk file on a node.
4. Download using `fileId`.
5. Validate:
   - download succeeds,
   - corrupted replica is skipped,
   - clean replica used for reconstruction,
   - repair restores missing/corrupt replica count.
6. Corrupt all replicas of one chunk and verify expected failure.

## Acceptance Criteria

- Every chunk has a stored checksum in metadata.
- Download rejects corrupted chunk data.
- Single-replica corruption does not break file download.
- Corrupted replicas are automatically repaired when possible.
- Replication factor remains satisfied after repair.

## Deliverables

- Updated `ChunkMetadata` with checksum.
- Master upload/download updated for checksum logic.
- Integrity-aware repair behavior integrated with existing Stage 3 repair.
- Stage 4 completion document with evidence logs.

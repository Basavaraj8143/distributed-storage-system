# Stage 2 Done - Replication (V2)

## Objective

Implement chunk replication so each chunk is stored on multiple storage nodes and can still be downloaded when one node is down.

## Implementation Summary

- Replication factor is set in master service:

```java
private static final int REPLICATION_FACTOR = 2;
```

- Chunk metadata now stores multiple node URLs per chunk (replica locations), not a single node.
- During upload, each chunk is sent to 2 different nodes.
- During download, master tries replica nodes one by one until one succeeds.

## Services and Ports

- Master: `http://localhost:8080`
- Storage Node 1: `http://localhost:5001`
- Storage Node 2: `http://localhost:5002`
- Storage Node 3: `http://localhost:5003`

## API Endpoints Used

- `POST /upload` on master
- `GET /download/{fileId}` on master
- `POST /storeChunk` on storage nodes
- `GET /getChunk/{chunkId}` on storage nodes

## Verification Performed

1. Uploaded file using master upload API.
2. Verified chunks were replicated to 2 nodes per chunk.
3. Stopped one storage node and downloaded file successfully (replica fallback worked).
4. Stopped both replica nodes for a chunk and download failed as expected.

## Stage 2 Result

Stage 2 is complete.

- Replication implemented (RF = 2)
- Basic fault-tolerant read implemented
- Tested with real node failure scenarios

## Current Limitations

- No automatic re-replication after node failure
- No heartbeat-based failure detection
- No self-healing yet

These will be handled in Stage 3.

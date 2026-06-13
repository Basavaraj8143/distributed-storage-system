# Stage 3 (Part 4-6) Detailed Documentation

## Scope

This document captures detailed implementation of:

- Part 4: Metadata update on failure
- Part 5: Under-replication detection
- Part 6: Re-replication workflow

## Background

After Stage 3 Parts 1-3, master can:

- receive heartbeats
- track active/failed nodes
- detect node failure by timeout

Parts 4-6 complete the recovery loop by repairing metadata and rebuilding lost replicas.

## Part 4 - Metadata Update on Failure

### Goal

Remove failed node references from chunk replica metadata so master does not try dead locations.

### Implementation

- Heartbeat now includes `nodeId` and `nodeUrl`.
- Master keeps node health and URL mapping in heartbeat service.
- Scheduled repair task obtains failed node URLs.
- Chunk metadata is scanned and failed URLs are removed from each chunk's `nodeUrls`.

### Core Behavior

- Before cleanup:
  - `chunkA -> [http://localhost:5001, http://localhost:5002]`
- If `5002` fails:
  - cleanup result:
  - `chunkA -> [http://localhost:5001]`

This creates a valid but under-replicated state for Part 5 and Part 6.

## Part 5 - Under-Replication Detection

### Goal

Identify chunks with replica count below replication factor.

### Rule

- `REPLICATION_FACTOR = 2`
- Under-replicated if:
  - `chunk.nodeUrls.size() < 2`

### Implementation

- Detection is integrated into scheduled repair workflow.
- For every chunk:
  - if replica count is below factor, chunk is marked for repair immediately.

### Why this design

- No separate queue required for current scope.
- Simple periodic scan is enough for project scale.

## Part 6 - Re-Replication Workflow

### Goal

Restore each under-replicated chunk to `REPLICATION_FACTOR = 2`.

### Workflow

1. Choose source from current replica list.
2. Fetch chunk bytes from source (`GET /getChunk/{chunkId}`).
3. Choose target from active node URLs not already storing chunk.
4. Store same chunk ID and bytes to target (`POST /storeChunk`).
5. Update metadata replica list with new target URL.

### Edge Handling

- No reachable source:
  - skip now, retry on next scheduled cycle.
- No available target:
  - skip now, retry on next scheduled cycle.
- Target write failure:
  - try next target candidate.

### Scheduling

- Repair scheduler runs every 10 seconds.
- It performs:
  - failed reference cleanup
  - under-replication repair

### Logging

- Master logs repair summary:
  - `Replication repair: removed N failed replica refs, added M replicas`

## End-to-End Example

1. Initial:
  - `chunkX -> [5001, 5002]`
  - `chunkY -> [5002, 5003]`
2. Node `5002` fails.
3. Failure detector marks node failed.
4. Part 4 cleanup:
  - `chunkX -> [5001]`
  - `chunkY -> [5003]`
5. Part 5 detection marks both chunks under-replicated.
6. Part 6 repair adds new replicas on active nodes:
  - `chunkX -> [5001, 5003]`
  - `chunkY -> [5003, 5001]`

Replication factor is restored.

## Result

Parts 4-6 are implemented and working:

- failed-node metadata cleanup
- under-replication detection
- automatic replica rebuild

This completes the recovery pipeline needed for Stage 3 fault tolerance.

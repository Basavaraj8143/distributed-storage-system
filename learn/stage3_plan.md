# Stage 3 Plan

## Objective

Build node-level fault tolerance on top of Stage 2 replication by adding heartbeat-based health monitoring and automatic re-replication.

## Current Status

- Part 1: Heartbeat System - Done
- Part 2: Node Health Tracking - Done
- Part 3: Failure Detection (Timeout Logic) - Done
- Part 4: Metadata Update on Failure - Done
- Part 5: Under-Replication Detection - Done
- Part 6: Re-Replication Workflow - Done
- Part 7: End-to-End Validation - Done

## Part 1 - Heartbeat System (Done)

- Storage nodes send heartbeat every 5 seconds.
- Master exposes `POST /heartbeat`.
- Master records heartbeat by `nodeId`.

## Part 2 - Node Health Tracking (Done)

- Master maintains:
  - `nodeLastSeen` (`Map<String, Long>`)
  - `activeNodes` (`Set<String>`)
  - `failedNodes` (`Set<String>`)
- Utility methods are centralized in heartbeat service.

## Part 3 - Failure Detection (Done)

- Master scheduled checker runs every 10 seconds.
- Timeout rule is 15 seconds.
- If heartbeat is stale:
  - remove node from `activeNodes`
  - add node to `failedNodes`
  - log node failure once

## Part 4 - Metadata Update on Failure (Done)

### Goal

Ensure chunk metadata does not keep failed nodes as valid replica locations.

### Tasks

1. On node failure, master marks node as failed via timeout checker.
2. Scheduled replication repair scans chunk metadata.
3. Failed node URLs are removed from chunk replica lists.
4. Chunks with replica count below replication factor are left for re-replication.

## Part 5 - Under-Replication Detection (Done)

### Goal

Identify chunks where `currentReplicaCount < REPLICATION_FACTOR`.

### Tasks

1. Under-replicated chunks are detected when `replicas.size() < REPLICATION_FACTOR`.
2. Detection is executed in scheduled repair workflow.
3. Repair logs include removed references and added replicas.

## Part 6 - Re-Replication Workflow (Done)

### Goal

Restore replication factor automatically for under-replicated chunks.

### Tasks

1. Source node is selected from currently available replicas.
2. Target node is selected from active nodes not already containing chunk.
3. Master fetches chunk bytes from source (`/getChunk/{chunkId}`).
4. Master stores chunk to target (`/storeChunk`).
5. Chunk metadata is updated with repaired replica location.
6. Basic edge handling implemented:
   - no reachable source -> skip and retry on next cycle
   - no available target -> skip and retry on next cycle
   - transfer failure -> try next candidate target

## Part 7 - End-to-End Validation (Done)

### Test Flow

1. Start master + 3 nodes.
2. Upload file and confirm RF=2 per chunk.
3. Kill one node and wait for failure detection.
4. Confirm metadata cleanup and under-replication detection.
5. Confirm re-replication restores RF=2 on healthy nodes.
6. Download file and verify integrity.

### Expected Runtime Signals

- Master logs:
  - `Node FAILED: node-X`
  - `Replication repair: removed N failed replica refs, added M replicas`
- Storage nodes:
  - heartbeat logs with both node ID and node URL

### Validation Result

- End-to-end flow has been executed successfully.
- Node failure was detected via timeout.
- Metadata cleanup and re-replication were triggered.
- File remained downloadable after single-node failure.

## Deliverables for Stage 3 Completion

- Automatic node failure detection
- Metadata cleanup after failure
- Automatic re-replication
- Verified file availability after node loss

## Final Stage 3 Status

Stage 3 is complete (Parts 1-7).

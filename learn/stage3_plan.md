# Stage 3 Plan

## Objective

Build node-level fault tolerance on top of Stage 2 replication by adding heartbeat-based health monitoring and automatic re-replication.

## Current Status

- Part 1: Heartbeat System - Done
- Part 2: Node Health Tracking - Done
- Part 3: Failure Detection (Timeout Logic) - Done
- Part 4: Metadata Update on Failure - Pending
- Part 5: Under-Replication Detection - Pending
- Part 6: Re-Replication Workflow - Pending
- Part 7: End-to-End Validation - Pending

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

## Part 4 - Metadata Update on Failure (Next)

### Goal

Ensure chunk metadata does not keep failed nodes as valid replica locations.

### Tasks

1. On node failure, scan chunk metadata.
2. Remove failed node from each chunk's replica list.
3. Track chunks that now have replica count below replication factor.

## Part 5 - Under-Replication Detection (Next)

### Goal

Identify chunks where `currentReplicaCount < REPLICATION_FACTOR`.

### Tasks

1. Build method to collect under-replicated chunks.
2. Expose internal utility for re-replication job input.
3. Add logs for visibility.

## Part 6 - Re-Replication Workflow (Next)

### Goal

Restore replication factor automatically for under-replicated chunks.

### Tasks

1. Choose source node from existing healthy replicas.
2. Choose target node from active nodes not already holding that chunk.
3. Copy chunk from source to target.
4. Update metadata with new replica location.
5. Handle edge cases:
   - no healthy source
   - no available target
   - transfer failure and retry strategy (basic)

## Part 7 - End-to-End Validation (Next)

### Test Flow

1. Start master + 3 nodes.
2. Upload file and confirm RF=2 per chunk.
3. Kill one node and wait for failure detection.
4. Confirm metadata cleanup and under-replication detection.
5. Confirm re-replication restores RF=2 on healthy nodes.
6. Download file and verify integrity.

## Deliverables for Stage 3 Completion

- Automatic node failure detection
- Metadata cleanup after failure
- Automatic re-replication
- Verified file availability after node loss


# Stage 3 (Part 1-3) Done - Heartbeat, Health Tracking, Failure Detection

## Scope Completed

This document covers Stage 3 implementation up to:

- Part 1: Heartbeat System
- Part 2: Node Health Tracking
- Part 3: Failure Detection (Timeout Logic)

## Part 1 - Heartbeat System

### Goal

Storage nodes periodically notify master that they are alive.

### Implemented

- In `storage-node`:
  - Enabled scheduling with `@EnableScheduling`.
  - Added scheduled task every 5 seconds to send heartbeat to master:
    - `POST http://localhost:8080/heartbeat`
    - JSON body includes `nodeId`.
  - Node IDs are resolved by port:
    - `5001 -> node-1`
    - `5002 -> node-2`
    - `5003 -> node-3`

- In `master-service`:
  - Added `POST /heartbeat` API.
  - Added request model containing `nodeId`.
  - Heartbeats are received and logged.

## Part 2 - Node Health Tracking

### Goal

Master should maintain clear node state, not only logs.

### Implemented

- In-memory structures in master:
  - `Map<String, Long> nodeLastSeen`
  - `Set<String> activeNodes`
  - `Set<String> failedNodes`

- On each heartbeat:
  - update `nodeLastSeen[nodeId] = currentTimeMillis`
  - add node to `activeNodes`
  - remove node from `failedNodes` if present

- Centralized utility methods added:
  - `getActiveNodes()`
  - `isNodeAlive(nodeId)`
  - `getNodeLastSeen()`
  - `getFailedNodes()`
  - `markNodeFailed(nodeId)`

## Part 3 - Failure Detection (Timeout Logic)

### Goal

Master should detect and mark dead nodes automatically.

### Implemented

- Enabled scheduling in `master-service`.
- Added background checker:
  - `@Scheduled(fixedRate = 10000)`
  - runs every 10 seconds
- Timeout threshold:
  - `NODE_TIMEOUT_MS = 15000` (15 seconds)

- Detection rule:
  - For each node in `nodeLastSeen`, if:
    - node is currently active, and
    - `now - lastSeen > 15000`
  - then:
    - remove from `activeNodes`
    - add to `failedNodes`
    - log: `Node FAILED: <nodeId>`

- Duplicate failure processing is avoided by checking `activeNodes.contains(nodeId)` before marking failed.

## Runtime Behavior Expected

1. Start master first, then storage nodes.
2. Master receives heartbeat logs every ~5 seconds from active nodes.
3. Stop one storage node.
4. After roughly 15-25 seconds, master logs `Node FAILED: node-X`.
5. Internal state reflects:
   - node removed from `activeNodes`
   - node added to `failedNodes`

## Current Storage Type

All health-tracking data is in-memory (runtime only).  
If master restarts, heartbeat state resets.

## Status

Stage 3 Parts 1-3 are completed and compiled successfully.

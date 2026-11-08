# Rack-Aware Replica Placement Plan

## Objective

Make sure the replicas of a chunk are never all inside one failure domain (a rack). With replication factor 2, every chunk should live on two different racks whenever two racks are available, so losing a whole rack loses no data.

## Current State (verified in code)

| Area | Today |
| --- | --- |
| Upload placement | `ChunkService.storeChunkOnReplicas` shuffles the ACTIVE configured nodes and takes the first 2 that accept the chunk. No topology. |
| Repair placement | `ChunkService.repairUnderReplicatedChunks` takes the first ACTIVE node that does not already hold the chunk. No topology. |
| Node identity | Heartbeat carries only `nodeId` and `nodeUrl` (`HeartbeatRequest`, `HeartbeatSenderService`). |
| Rack concept | None. Nothing in config, metadata, API or UI. |

Result: both replicas of a chunk can land on nodes that share a failure domain, and repair can re-create the same problem.

## Design Decisions

1. **Where the rack comes from.** Each storage node reports its own rack in its heartbeat, from a new `STORAGE_RACK` setting. A node with no rack reports nothing and the master treats it as `default-rack`. Old nodes keep working. Placement only ever considers ACTIVE nodes, and an ACTIVE node has always sent a heartbeat, so its rack is always known.
2. **One small policy class.** `ReplicaPlacementPolicy` exposes a single method: `Optional<String> pickNext(Collection<String> candidates, Collection<String> alreadyChosen)`. One method composes with both upload (with retry on a failed send) and repair.
3. **Greedy rule.** Pick from the candidates whose rack currently holds the fewest of the chosen replicas. Break ties randomly. For replication factor 2 this means "a different rack if one exists". For factor 3 it gives a 2+1 split across two racks.
4. **Fallback, never failure.** If every candidate is on a rack that already holds a replica, pick any other node. Availability wins over placement. The fallback is logged.
5. **No data stays in the wrong place forever.** A chunk placed under the fallback is fixed later by a rack-violation repair (Phase 5), once a second rack is available again.
6. **Rack is not stored in the database.** It is read from the live heartbeat data when a decision is made, so renaming a rack needs no migration.

## Implementation Phases

### Phase 1: Report and track racks

1. Add an optional `rack` field to `HeartbeatRequest`.
2. Add `storage.rack` (env `STORAGE_RACK`) to the storage node and include it in the heartbeat.
3. In `HeartbeatService`, keep a `nodeId -> rack` map, updated on every heartbeat, and add `getRack(nodeUrl)` returning `default-rack` when unknown.
4. Return the rack in `GET /nodes/status`.
5. In `compose.yaml`, set `STORAGE_RACK` on all 5 nodes. Suggested layout: `rack-a` = nodes 1 and 2, `rack-b` = nodes 3 and 4, `rack-c` = node 5.

**Exit criteria:** `/nodes/status` shows the rack for every node, and a node without `STORAGE_RACK` shows `default-rack`.

### Phase 2: Placement policy

1. Add `ReplicaPlacementPolicy` with `pickNext(candidates, alreadyChosen)` and the greedy rule above.
2. Inject a rack lookup (`HeartbeatService::getRack`) so the policy can be unit tested with a plain function.
3. Log when the fallback is used.

**Exit criteria:** the policy passes its unit tests (see Testing) without starting Spring.

### Phase 3: Use the policy

1. **Upload.** Replace the shuffle in `storeChunkOnReplicas` with a loop: `pickNext` over the remaining candidates, send, and on failure remove that node and pick again. Keep `InsufficientNodesException` when fewer than the replication factor can be stored.
2. **Repair.** In `repairUnderReplicatedChunks`, replace "first candidate" with `pickNext(candidateTargets, currentReplicas)` so a replacement lands on a rack the survivor is not on.

**Exit criteria:** with two racks active, every newly uploaded or repaired chunk has replicas on two different racks.

### Phase 4: Visibility and demo

1. Node Status page: add a Rack column.
2. File details (`GET /files/{id}`): list the rack beside each node URL, and show a "rack-safe" indicator when a chunk spans at least 2 racks.
3. Simulation: add "take rack offline", which simulates every node in a rack offline. This is the demo: kill a rack, then show that every file still downloads and that repair rebuilds the second copy on another rack.

**Exit criteria:** a rack can be failed from the UI and all files stay downloadable.

### Phase 5: Rack-violation repair (optional)

1. In the repair run, find chunks that have the full replica count but fewer distinct racks than `min(replication factor, racks currently available)`.
2. For each, copy the chunk to a node on a missing rack first, record it, and only then remove one replica from the over-represented rack (delete the record, then `DELETE /deleteChunk/{id}` on that node). Copy before delete, so the chunk is never under-replicated during the move.
3. Limit how many chunks are moved per run so a recovered rack does not trigger a burst of copying.

**Exit criteria:** after a rack returns, chunks that were placed under the fallback spread back across racks within a few repair runs.

## Interaction With Existing Behaviour

- **STALE nodes** are not candidates, as today.
- **Reconcile of a returning node** keeps its current rule (surplus copy deleted, needed copy adopted). Rack diversity is restored by Phase 5, not by reconcile.
- **Downloads** are unchanged.

## Testing

`ReplicaPlacementPolicyTest` (pure unit test):

- Two racks: the two chosen replicas are never on the same rack, over many random runs.
- Existing replica on rack-a: the next pick is on another rack.
- All candidates on the same rack as the existing replica: the fallback picks a different node, not an error.
- A single rack only: two distinct nodes are still chosen.
- No candidates: returns empty.
- Replication factor 3 over two racks: a 2+1 split.

Service tests (update `ChunkIntegrityTest`, add repair cases):

- Upload with a failed send retries on another node and still spans two racks.
- Repair of a chunk with one survivor on rack-a places the new replica on a different rack.
- Fallback case: only rack-a is active, so the upload still succeeds, with a warning logged.

Manual: start all 5 nodes, upload several files, take `rack-a` offline, and download every file.

## Risks and Mitigations

| Risk | Mitigation |
| --- | --- |
| Only one rack is alive, so replicas share a rack | Fallback keeps uploads working; Phase 5 restores diversity when a second rack returns. |
| Rack skew: a small rack fills faster | Out of scope for the first version. Later, break ties by fewest stored chunks instead of randomly. |
| A node reports a changed rack after a restart | Rack is read live, so decisions follow the new value. Existing chunks may then need Phase 5 to move. |
| Misconfigured racks (all nodes the same value) | Everything falls back to today's random behaviour. The Rack column makes it visible. |
| Old storage node builds send no rack | Treated as `default-rack`. Nothing breaks. |

## Likely Files to Change

- `master-service/.../model/HeartbeatRequest.java`
- `master-service/.../service/HeartbeatService.java`
- `master-service/.../service/ChunkService.java`
- New `master-service/.../service/ReplicaPlacementPolicy.java`
- `master-service/.../controller/MonitoringController.java`
- `master-service/.../model/ChunkInfo.java` and `FileDetails.java` (rack per replica)
- `master-service/.../controller/SimulationController.java` (rack offline)
- `storage-node/.../service/HeartbeatSenderService.java`, `storage-node/src/main/resources/application.properties`
- `compose.yaml`
- `client-ui/src/pages/NodeStatusPage.jsx`, `FilesPage.jsx`, `SimulationPage.jsx`, `src/api/index.js`
- Tests: new `ReplicaPlacementPolicyTest`, updates to `ChunkIntegrityTest`

## Acceptance Checklist

- [ ] Every node reports a rack, and `default-rack` is used when none is set.
- [ ] With two or more racks active, no chunk is uploaded or repaired onto a single rack.
- [ ] With one rack active, uploads still succeed and a warning is logged.
- [ ] A whole rack can be taken offline from the UI and all files still download.
- [ ] Repair after a rack failure places the new replica on a different rack from the survivor.
- [ ] File details show the rack of each replica.
- [ ] Policy unit tests and updated service tests pass.
- [ ] (Optional) Rack-violation repair restores diversity after a rack returns.

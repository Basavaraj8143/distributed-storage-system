# Repair Cycle

How the master keeps every chunk at the target replica count when nodes go quiet, die, come back, or hold bad data.

Source: `HeartbeatService`, `ReplicationRepairService`, `ChunkRepairService` in `master-service`.

## 1. Constants

| Setting | Value | Where |
| --- | --- | --- |
| Replication factor | 2 (default) | `storage.replication-factor` (env `STORAGE_REPLICATION_FACTOR`) |
| Heartbeat sent by each node | every 5 s | `HeartbeatSenderService` |
| Liveness check | every 5 s | `HeartbeatService.checkNodeFailures` |
| STALE after silence of | 15 s | `heartbeat.stale-timeout-ms` |
| FAILED after silence of | 60 s | `heartbeat.dead-timeout-ms` |
| Repair job | every 10 s, first run after 5 s | `ReplicationRepairService` |

The dead timeout must be larger than the stale timeout, or the master refuses to start.

Throughout this document "2 replicas" means the configured replication factor, whose default is 2. The master also refuses to start if the factor is larger than the number of nodes in `storage.nodes`.

Changing the factor on a cluster that already holds data:

- **Raised (for example 2 to 3):** the repair run sees every existing chunk as under-replicated and adds the missing copies. Expect a burst of copying.
- **Lowered (for example 3 to 2):** nothing happens. Repair only adds missing copies and never removes extra ones, so existing chunks keep their extra copy. A trim step does not exist yet.

## 2. Node states

| State | Entered when | Uploads | Repair target | Replica records |
| --- | --- | --- | --- | --- |
| ACTIVE | A heartbeat arrives | Yes | Yes | Kept |
| STALE | Silent for more than 15 s | No | No | Kept, nothing is copied |
| FAILED | Silent for more than 60 s | No | No | Dropped, chunks re-replicated |

Transitions on a heartbeat:

- STALE to ACTIVE: no further action. No data was moved.
- FAILED to ACTIVE: the node is queued for reconciliation (section 4).
- First heartbeat since the master started: queued for reconciliation.

Node state is held in memory only. A master restart forgets it until heartbeats arrive again.

## 3. One repair run

Every 10 s, in this order:

1. **Reconcile returning nodes.** For each node queued as rejoined, compare its stored chunks with the metadata. Runs first so a valid chunk the node still holds is adopted before step 4 copies it elsewhere.
2. **Drop FAILED replicas.** Remove every replica record that points at a FAILED node.
3. **Integrity scan.** For every chunk, read each replica on an ACTIVE node and compare its SHA-256 with the checksum saved at upload. Overwrite a bad copy from a good one. If the overwrite fails, drop that replica record. If no copy is valid, log `CRITICAL`.
4. **Re-replicate.** For each chunk with fewer than 2 recorded replicas, read it from a surviving replica, send it to ACTIVE nodes that do not hold it yet until the count is back to 2, and record the new replica.

If a chunk has no readable replica, step 4 skips it. If no ACTIVE node is free, it stays under-replicated and is retried on the next run.

## 4. Reconciling a returning node

The master calls `GET /chunks` on the node and checks each chunk ID.

| Chunk the node reports | Action |
| --- | --- |
| Already recorded for this node | None |
| Known, and 2 or more ACTIVE replicas elsewhere | Delete the node's copy (`DELETE /deleteChunk/{id}`). It is surplus. |
| Known, fewer than 2 ACTIVE replicas elsewhere, checksum valid | Adopt it: write the replica record back. No new copy is made. |
| Known, checksum does not match | Delete the node's copy. |
| Unknown to the master | Leave it, count it in the log |

- A failure on one chunk skips only that chunk.
- If `/chunks` itself fails, the node is queued again and retried on the next run.
- A node without the `/chunks` endpoint logs a warning and is not retried.

## 5. Timeline: node silent for 10 minutes, then back

`t = 0` is the node's last heartbeat.

| Time | Event | Metadata |
| --- | --- | --- |
| 0 to 15 s | Heartbeats missed, node still ACTIVE | Unchanged |
| 15 s | STALE: no new uploads | Unchanged |
| 60 s | FAILED | Its replica records dropped, chunks at 1 replica |
| Next repair run | Chunks copied from survivors to other ACTIVE nodes | Back to 2 replicas each |
| 60 s to 10 min | Node's old chunk files remain on its disk, unknown to the master | |
| Node returns | Heartbeat, state ACTIVE, queued for reconciliation | |
| Next repair run | Reconcile runs first: surplus copies deleted, still-needed copies adopted | Every chunk at exactly 2 replicas |

## 6. Upload and delete interaction

- Upload picks only nodes that are in `storage.nodes` and ACTIVE. If a send fails it tries the next node. With fewer than 2 reachable nodes it returns 503.
- Delete removes the metadata first, then removes the chunk files from the nodes after the commit. A node that is down keeps its copy. That copy becomes an unknown chunk when the node returns, and is left in place.

## 7. Known limitations

- **Unknown chunks are never deleted.** `/chunks` returns IDs only, with no age, so a deleted file's leftover cannot be told apart from an upload still in progress.
- **The integrity scan reads every replica of every chunk every 10 s.** Fine at demo scale, not beyond it.
- **Re-replication does not verify the source copy.** Step 4 copies the first readable replica. A corrupt source is caught by the next integrity scan, not before copying.
- **A node that dies while the master is down is never marked FAILED.** State is in memory, so its replica records stay until it is seen again.
- **Repair holds a database transaction while calling nodes.** With SQLite's single writer, a long run can delay uploads.
- **Downloads try replicas in recorded order,** including STALE ones, before falling back.
- **The master is a single point of failure.**

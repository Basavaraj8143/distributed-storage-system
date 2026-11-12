# Splitting ChunkService

`ChunkService` did everything the master does with chunks: upload, download, list, delete, repair, reconcile, plus the HTTP calls to the nodes and the checksum code. It had about 600 lines and six reasons to change. It is now split by responsibility.

Source: `master-service/src/main/java/com/byteharvest/master/`

## 1. Why

| Problem | Effect |
| --- | --- |
| One class, many responsibilities (violates Single Responsibility) | Any change risked breaking unrelated features |
| `RestTemplate` and URL strings used everywhere | Could not test or replace the node transport without mocking `RestTemplate` URLs |
| Copy-pasted SHA-256 and replica-list code | Same bug would need fixing in several places |
| Six constructor dependencies, most unused by any one method | Hard to read, hard to test |

## 2. The new layout

| Class | Package | Responsibility |
| --- | --- | --- |
| `UploadService` | `service` | Split a file into chunks, store each on the configured number of active nodes |
| `DownloadService` | `service` | Reassemble a file, verify each chunk's checksum, fix corrupt copies found on the way |
| `FileManagementService` | `service` | List files, file details, original filename, delete a file |
| `ChunkRepairService` | `service` | Repair operations: drop failed replicas, fix corruption, add missing copies, reconcile a returning node |
| `ReplicationRepairService` | `service` | Unchanged role: the scheduler that decides *when* repair runs, and calls `ChunkRepairService` |
| `StorageNodeClient` (interface) | `client` | What the master can ask a node: store, fetch, delete, list chunk ids |
| `RestStorageNodeClient` | `client` | The REST implementation of that interface |
| `Checksums` | `util` | `sha256(byte[])`, a pure static helper |

`ChunkMetadataEntity` also gained `replicaUrls()` (the distinct nodes holding a chunk). Two services needed that logic, and it is a fact about the chunk, so it lives on the chunk.

## 3. Who uses what

```
FileController ──> UploadService ─────────┐
               ──> DownloadService ───────┤
               ──> FileManagementService ─┼──> StorageNodeClient ──> storage nodes
                                          │
ReplicationRepairService ──> ChunkRepairService ──┘
```

Every service talks to nodes only through `StorageNodeClient`. Only `RestStorageNodeClient` knows about `RestTemplate` and the `/storeChunk`, `/getChunk/{id}`, `/deleteChunk/{id}` and `/chunks` paths.

## 4. Where the old methods went

| Old `ChunkService` method | New home |
| --- | --- |
| `handleUpload` (both versions), `storeChunkOnReplicas` | `UploadService` |
| `download`, `fetchChunkFromReplicas` | `DownloadService` |
| `listFiles`, `getFileDetails`, `getOriginalFileName`, `deleteFile` | `FileManagementService` |
| `removeFailedNodeReferences`, `repairUnderReplicatedChunks`, `verifyAndRepairCorruptions`, `reconcileNode` | `ChunkRepairService` |
| `sendChunkToNode`, the `restTemplate.*` calls | `RestStorageNodeClient` |
| `calculateSha256` | `Checksums.sha256` |

The logic was moved, not rewritten. Behaviour, endpoints and JSON are unchanged.

## 5. Design points

- **Single Responsibility.** Each service has one reason to change. Changing how deletes work no longer touches the upload code.
- **Dependency Inversion.** Services depend on the `StorageNodeClient` interface, not on `RestTemplate`. A test can supply a fake client, and the transport (for example gRPC) could change without touching a service.
- **Dependencies shrank.** `DownloadService` needs 4 collaborators, `FileManagementService` 4, `ChunkRepairService` 4, `UploadService` 6. Before, everything needed 6.
- **Why `Checksums` is static and the client is not.** `sha256` has no state and no collaborators to swap, so a bean adds nothing. The client wraps I/O that tests want to replace, so it is an injected interface.
- **Name clash to remember.** `ReplicationRepairService` is the scheduler and `ChunkRepairService` does the work. If that stays confusing, rename the scheduler to `RepairScheduler`.

## 6. Tests

| Test | Now exercises |
| --- | --- |
| `ChunkIntegrityTest` | `UploadService`, `DownloadService`, `ChunkRepairService` |
| `FileManagementServiceTest` | `FileManagementService`, `DownloadService` |
| `NodeReconcileTest` | `ChunkRepairService` |
| `FileControllerTest` | `FileController` with the three services mocked |

Tests wrap the mocked `RestTemplate` in a real `RestStorageNodeClient`, so every assertion on node URLs still holds.

## 7. Known limitations (not fixed by the split)

- `verifyAndRepairCorruptions` (repair) and `fetchChunkFromReplicas` (download) both have corruption-handling code that is similar. A shared integrity checker would remove the overlap.
- `DownloadService.download` is `readOnly = true` but can write (it removes a replica record when an overwrite fails). It was like this before.
- `ChunkReplicaRepository` is no longer used by any service.
- Uploads are still not atomic: a failure halfway leaves the chunks already stored.

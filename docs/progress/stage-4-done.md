# Stage 4 Done - Data Integrity & Auto-Repair (V3.5)

## Objective

Implement chunk-level integrity validation using SHA-256 checksums to detect corrupted chunks on read or during periodic checks, automatically fetch them from a healthy replica, and rewrite/repair the corrupt replicas.

---

## Implementation Summary

### 1. Metadata Extension
- Extended `ChunkMetadata` to store a `checksum` field.
- During file upload in `ChunkService.handleUpload`, a SHA-256 checksum is calculated for each split chunk and saved in the master's metadata model.

### 2. Checksum Verification on Read (Download)
- In `ChunkService.download`, the master iterates through the replicas for each chunk:
  - Checks if the calculated SHA-256 of the fetched replica bytes matches the expected checksum.
  - If a checksum mismatch occurs, logs a warning, flags the node as corrupted, and falls back to check the next replica.
  - Returns the first valid replica.

### 3. Self-Healing & Overwrite Recovery
- Once valid chunk bytes are successfully retrieved:
  - The master immediately attempts to restore integrity by rewriting the valid chunk bytes back to the corrupted replica nodes (`/storeChunk`).
  - If the overwrite rewrite fails (e.g. because the node is offline/unreachable), the master removes that node from the chunk's `nodeUrls` replica list.
  - The next run of the scheduled repair task will automatically identify this as an under-replicated chunk and copy it to a new active node.

### 4. Integrity-Aware Periodic Repair
- Extended `ReplicationRepairService.repairReplication` scheduled checker (which runs every 10 seconds).
- It calls the new `verifyAndRepairCorruptions` service *before* running under-replicated chunk repairs.
- Active node replicas are periodically verified for checksum consistency. Any detected corruption is repaired or stripped from chunk mappings, enabling the under-replication scheduler to relocate the chunk.

---

## Files Changed/Added

- [MODIFY] [ChunkMetadata.java](file:///d:/projects/final/master-service/src/main/java/com/byteharvest/master/model/ChunkMetadata.java) — added checksum properties.
- [MODIFY] [ChunkService.java](file:///d:/projects/final/master-service/src/main/java/com/byteharvest/master/service/ChunkService.java) — added checksumming, verification, self-healing, and repair service.
- [MODIFY] [ReplicationRepairService.java](file:///d:/projects/final/master-service/src/main/java/com/byteharvest/master/service/ReplicationRepairService.java) — hooked periodic corruption repair logic.
- [NEW] [ChunkIntegrityTest.java](file:///d:/projects/final/master-service/src/test/java/com/byteharvest/master/ChunkIntegrityTest.java) — unit tests covering integration flows.

---

## Verification Performed

### 1. Automated Unit Tests
A dedicated unit test suite was implemented in `ChunkIntegrityTest.java` containing:
- `testUploadGeneratesChecksum`: Asserts uploading generates valid 64-character SHA-256 checksums in chunk metadata.
- `testDownloadVerifiesIntegrityAndRepairsCorruption`: Asserts download ignores a corrupt node, grabs correct bytes from a healthy node, and successfully overwrites the corrupt node.
- `testDownloadRemovesReplicaOnRepairFailure`: Asserts that when overwriting/repairing a corrupt node throws a write failure, the corrupt node is stripped from chunk replicas.
- `testScheduledIntegrityVerificationAndRepair`: Asserts scheduled corruption verification scans and repairs corrupted nodes.

**Test Results:**
```text
[INFO] Running com.byteharvest.master.ChunkIntegrityTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.349 s -- in com.byteharvest.master.ChunkIntegrityTest
[INFO] Running com.byteharvest.master.MasterServiceApplicationTests
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.338 s -- in com.byteharvest.master.MasterServiceApplicationTests
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

### 2. Service Co-existence
- The master service and 3 storage nodes were run concurrently. Heartbeats registered successfully, and the system registered `ACTIVE` states under standard operations.

---

## Next Steps
- Continue verifying backend CORS compatibility with browser clients under other domains if needed.
- Connect the React front-end page controls for logs and upload to the running backend instances.

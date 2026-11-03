# Scalable Distributed Storage System with Replication and Fault Tolerance
## Project Master README and Context Document for Report Preparation

> **Purpose of this document:** This README consolidates the project context, **actual implemented architecture**, design concepts, implementation status, terminology, semester-wise improvements, testing results, and report-writing instructions. It is intended to be supplied to Claude (or another writing assistant) as the main context document for preparing the academic project report.
>
> **Important accuracy rule:** This document has been updated to reflect the **actual implemented features** as verified against the source code and test outputs. Planned/future features are clearly marked as such.

---

# 1. Project Identity

- **Project title:** Scalable Distributed Storage System with Replication and Fault Tolerance
- **Theme:** Distributed Systems
- **Team name:** Byte-Harvest
- **Academic year:** 2026–2027
- **Project guide:** Geeta Bharamagoudar
- **Team members:**
  - Basavaraj N
  - Akash M K
  - Ishan Patil
  - Disha H
- **Development environment:** One laptop (Windows + WSL Ubuntu), running the master and multiple storage nodes as separate processes on different localhost ports.
- **Extension status:** Multi-laptop deployment is **planned (Stage 5)** but not yet implemented/tested.
- **Primary implementation language/framework:** **Java 17 with Spring Boot 3.5.12** (Maven build), with a **React (Vite) frontend** for the dashboard.
- **Project category:** Academic major project / educational distributed storage prototype.

---

# 2. Executive Summary

The project implements a distributed file storage system that divides files into smaller chunks (blocks) and stores those chunks across multiple storage nodes. A master node coordinates storage operations, maintains metadata, tracks storage nodes, and decides where chunks should be placed. **Replication is implemented with a replication factor of 2**, keeping copies of chunks on different nodes so that data remains available when an individual storage node fails.

The system **uses heartbeat messages to monitor node health** (every 5 seconds). When a node stops sending heartbeats for longer than the configured timeout (15 seconds), the master marks it as unavailable (FAILED), checks which chunks have lost replicas, and **initiates automatic re-replication from a surviving copy**. **SHA-256 checksums are implemented** to detect data corruption, with automatic self-healing that rewrites corrupted chunks from healthy replicas. The client supports file upload, download, and reconstruction from ordered chunks.

Development was done on one laptop (Windows + WSL Ubuntu), where each node runs as a separate Java process and uses a separate storage directory (`storage_5001`, `storage_5002`, `storage_5003`, etc.). The design is inspired by concepts found in distributed file systems such as the Google File System (GFS) and Hadoop Distributed File System (HDFS), but the project is a simplified educational implementation rather than a reproduction of either production system.

**Multi-laptop deployment is planned (Stage 5 - Docker) but not yet implemented.**

The central learning and engineering goals demonstrated are: block-based storage, data placement, replication, metadata management, failure detection, recovery, data integrity verification, and the trade-offs involved in building a distributed system.

---

# 3. Abstract

The rapid growth of digital data has increased the need for storage systems that can scale beyond a single machine while maintaining data availability and reliability. Traditional centralized storage can be constrained by the capacity of one server and can introduce a single point of failure. This project implements a **Scalable Distributed Storage System with Replication and Fault Tolerance**.

The system divides files into smaller chunks and distributes them across multiple storage nodes. A master node coordinates the system by maintaining file and chunk metadata, tracking storage-node health, and selecting storage locations. **Replication is implemented with a replication factor of 2**, maintaining copies of chunks on different nodes so data remains available when a storage node fails. **Heartbeat-based monitoring** (every 5 seconds, with a 15-second timeout) identifies unavailable nodes, and **automatic re-replication** restores replicas when a healthy source copy exists. **SHA-256 checksums** detect data corruption, with self-healing that rewrites corrupted chunks from healthy replicas. The client supports file upload, download, and reconstruction from ordered chunks.

Development was done on one laptop (Windows + WSL Ubuntu), where the master and storage nodes run as separate Spring Boot processes on different ports. Multi-laptop deployment via Docker is **planned (Stage 5)** but not yet implemented.

**Status:** All core features described below are implemented, integrated, and tested with passing unit tests. Docker deployment and multi-laptop testing remain planned future work.

---

# 4. Introduction and Background

Modern applications produce and consume increasing volumes of files and other digital data. Storing everything on one machine can create capacity limits, a performance bottleneck, and a single point of failure. Distributed storage addresses these issues by placing data across several machines and coordinating access to it through software.

In a block-based distributed storage system, a file is divided into smaller pieces called **chunks** (also referred to as **blocks**). The chunks can be placed on different storage nodes, and the system records metadata describing the original file, its ordered chunks, and the locations of the chunk copies. When a client requests a file, the system locates the chunks, retrieves them, and combines them in the correct order.

**Replication is implemented with a replication factor of 2**, keeping copies of chunks on different nodes. If a storage node becomes unavailable, a surviving copy may still serve a read request. A recovery process can then create a replacement replica on another healthy node. Replication has a cost: storing two copies of every chunk requires approximately two times the raw chunk storage before accounting for metadata and other overhead.

A distributed storage system must also distinguish between a node that has actually failed and one that is merely slow or temporarily disconnected. Heartbeat timeouts are a practical failure-detection technique, but a missed heartbeat does not prove with certainty that a machine has crashed. The design therefore uses clear node states (ACTIVE/FAILED/UNKNOWN) and a configurable timeout value, and it avoids creating duplicate replicas during uncertain or repeated failure events.

This project implements and demonstrates these concepts with a modular architecture that is small enough to understand and test, while retaining the core control flow of a distributed storage system. The implementation is written in **Java 17 with Spring Boot 3.5.12**, using **REST/HTTP** for communication between master, storage nodes, and client, with **local file-system storage** for chunk data and **SQLite** for metadata persistence.

---

# 5. Problem Statement

Centralized storage architectures can become constrained by the storage capacity and processing resources of one machine, and failure of that machine may interrupt access to stored data. A distributed architecture can distribute storage across several nodes, but it introduces new challenges: deciding where data should be placed, keeping track of chunk locations, maintaining replicas, detecting unavailable nodes, and restoring redundancy after failures.

The problem addressed by this project is to design a lightweight, modular distributed storage prototype implemented in Java/Spring Boot that can:

1. Divide uploaded files into chunks of 1 MiB each.
2. Distribute chunks across available storage nodes using round-robin placement.
3. Maintain metadata linking files, ordered chunks, and replica locations in an SQLite database.
4. Replicate chunks according to a configured replication factor (default: RF = 2).
5. Monitor storage-node health through heartbeat messages (every 5 seconds) and timeout-based detection (15 seconds).
6. Detect under-replicated chunks after node failures.
7. Re-create missing chunks from healthy copies where possible via automatic re-replication.
8. Verify stored chunk contents with SHA-256 checksums, with self-healing that repairs corrupted chunks.
9. Retrieve and reconstruct uploaded files from ordered chunks.
10. Be developed and tested locally before being extended to multiple physical machines.

The project is an educational prototype implemented as a Java Spring Boot application with a React frontend. It should not be described as production-ready or as providing guaranteed availability unless those claims are supported by implementation details and test results.

---

# 6. Motivation: Why This Project Was Chosen

The project combines several important computer science topics in one practical system:

- Distributed computing and network communication.
- File systems and chunk-based storage.
- Data distribution and placement algorithms (round-robin).
- Replication and redundancy (RF=2).
- Metadata management (SQLite + JPA).
- Failure detection and recovery (heartbeats + re-replication).
- Data integrity using SHA-256 checksums.
- Scalability and load distribution.
- Service modularity and deployment.

Studying systems such as GFS and HDFS provides an opportunity to understand how large-scale storage systems organize data and handle failures. Building a smaller prototype makes these concepts observable: a file can be uploaded, chunks can be inspected on different nodes, a node process can be stopped, and the resulting effect on replica availability can be measured. The project demonstrates a complete end-to-end workflow from upload through failure to recovery.

---

# 7. Project Objectives

## 7.1 Main objective

To design and develop a scalable distributed storage prototype implemented in Java/Spring Boot that distributes file chunks across multiple storage nodes and demonstrates replication, heartbeat-based failure detection, automatic re-replication, and data integrity verification.

## 7.2 Specific objectives

1. Design a master-and-storage-node architecture using Spring Boot REST services.
2. Implement file chunking (1 MiB blocks) and preserve chunk order for reconstruction.
3. Develop a round-robin block-placement algorithm to select storage nodes.
4. Maintain metadata about files, chunks, checksums, and replica locations in an SQLite database.
5. Implement a configurable replication factor (default: RF = 2).
6. Add heartbeat messages (every 5 seconds) and timeout-based node monitoring (15-second timeout).
7. Detect under-replicated chunks after node failures.
8. Re-create missing replicas from healthy copies where possible via automatic re-replication.
9. Verify stored chunk contents with SHA-256 checksums, with self-healing repair.
10. Support file upload, download, and reconstruction.
11. Test the system on one laptop using separate processes and ports.
12. Prepare the architecture for later multi-laptop deployment (Docker).
13. Measure correctness, recovery behavior, storage overhead, and performance using repeatable experiments.

**Status:** All objectives 1-11 are **implemented and tested**. Objective 12 (Docker deployment) and 13 (measured performance results) are **planned or partially complete**.

---

# 8. Scope of the Study

## In scope

The intended project scope includes:

- A central master/coordinator service.
- Multiple storage-node services.
- A client-side upload and download workflow.
- File splitting into blocks.
- Block placement across nodes.
- Replication and replica-location metadata.
- Node registration and heartbeat monitoring.
- Timeout-based failure detection.
- Re-replication after a node failure.
- Checksum-based integrity verification.
- Metadata persistence for a small prototype.
- Local multi-process deployment.
- Later deployment across multiple physical laptops.
- A simple status view or API for observing nodes, files, blocks, and replica health, if time permits.

## Out of scope unless deliberately added and tested

The initial project should not claim to implement the following unless they are actually built:

- Production-grade multi-master coordination.
- Raft or Paxos consensus.
- Guaranteed strong consistency under all network partitions.
- Cloud-scale operation.
- Production security, authentication, and encryption.
- Erasure coding.
- Cross-region replication.
- Enterprise-grade access control.
- High availability of the master itself.
- Formal proof of durability or availability.
- Production benchmarks at large scale.

---

# 9. System Architecture

## 9.1 High-level architecture

```text
                         +------------------+
                         |      Client      |
                         | Upload / Download|
                         +---------+--------+
                                   |
                                   v
                         +------------------+
                         |    Master Node   |
                         |------------------|
                         | Node Registry    |
                         | Metadata Manager |
                         | Block Placement  |
                         | Replication Mgmt |
                         | Failure Detector |
                         +--+-------+----+--+
                            |       |    |
                ------------+       |    +------------
                |                   |                 |
                v                   v                 v
       +----------------+  +----------------+  +----------------+
       | Storage Node 1 |  | Storage Node 2 |  | Storage Node 3 |
       | Block storage  |  | Block storage  |  | Block storage  |
       | Heartbeats     |  | Heartbeats     |  | Heartbeats     |
       +----------------+  +----------------+  +----------------+
```

The client contacts the master to obtain instructions and metadata. Actual block data should be transferred to and from storage nodes. The master coordinates operations but should not need to relay every byte of every file unless the implementation intentionally chooses that design.

## 9.2 Master node responsibilities

The master is the control plane of the prototype. Its responsibilities are intended to include:

- Register storage nodes and record their endpoints.
- Track each node's status and last heartbeat.
- Maintain file metadata and block ordering.
- Record block sizes and checksums.
- Select storage nodes for new blocks and replicas.
- Keep track of the locations of block replicas.
- Return block locations to clients for downloads.
- Identify blocks that have fewer healthy replicas than required.
- Coordinate re-replication.
- Expose system status for testing and demonstration.

The master is a potential single point of failure in the initial architecture. Persisting metadata can help restart the master, but it does not by itself make the master highly available. Multi-master operation and consensus are outside the initial scope unless implemented as a later extension.

## 9.3 Storage node responsibilities

Each storage node is an independent service. It is intended to:

- Register itself with the master.
- Store blocks on local disk.
- Return a requested block.
- Accept a replica copied from another node or from the client, depending on the chosen upload design.
- Report its health through heartbeat messages.
- Provide block metadata such as size and checksum where appropriate.
- Reject requests for missing blocks.
- Support controlled shutdown or simulated failure during testing.

Each node should use its own storage directory. In a single-laptop simulation, the directories must be separate so that the project can demonstrate different node contents.

## 9.4 Client responsibilities

The client is intended to:

- Select a file to upload.
- Split the file into ordered blocks, or follow a master-directed upload plan.
- Request a placement plan from the master.
- Send blocks to selected storage nodes.
- Handle upload errors and acknowledgements.
- Request file metadata before download.
- Retrieve blocks from healthy replica locations.
- Verify checksums when downloading.
- Reassemble blocks in the original order.
- Report success or failure to the user.

The exact division of chunking responsibilities between client and master should be documented consistently in the final implementation. A practical design is for the client to split the file and the master to choose placement, avoiding the need to send the entire file through the master.

---

# 10. Actual Repository / Folder Structure (Implemented)

The actual repository follows a Java/Spring Boot layout rather than the originally proposed Python layout.

The directory structure (after building and running):

```text
final/
├── master-service/
│   ├── src/main/java/com/byteharvest/master/...
│   ├── src/test/java/com/byteharvest/master/ChunkIntegrityTest.java
│   └── pom.xml
├── storage-node/
│   ├── src/main/java/com/byteharvest/storage_node/...
│   └── pom.xml
├── client-ui/
│   ├── src/  (React + Vite)
│   └── dist/
├── docs/
├── storage_5001/  (chunk files)
├── storage_5002/
├── storage_5003/
└── logs/
```

This is a recommended structure, not confirmation that these exact files already exist. The final README and report should be updated to match the actual repository.

## Module responsibilities (implemented)

| Component | Responsibility |
|---|---|
| `MasterServiceApplication` | Spring Boot master entry point |
| `controller/FileController` | `POST /upload`, `GET /download/{fileId}` |
| `controller/HeartbeatController` | `POST /heartbeat` |
| `controller/MonitoringController` | `GET /nodes/status`, `GET /system/health` |
| `controller/SimulationController` | `POST /api/simulation/nodes/{nodeId}/offline|online` |
| `controller/LogsController` | `GET /logs`, `GET /logs/master` |
| `service/ChunkService` | Upload orchestration, chunk splitting, placement, download/verify/repair |
| `service/HeartbeatService` | Node health tracking, heartbeat timeout (15s) |
| `service/ReplicationRepairService` | Scheduled re-replication and corruption repair |
| `service/EventLogService` | In-memory event log |
| `service/MasterLogService` | Master log tailing |
| `entity/*` | JPA entities (File, Chunk, ChunkReplica) |
| `repository/*` | Spring Data JPA repositories |
| `model/*` | Plain models (FileMetadata, ChunkMetadata, HeartbeatRequest) |
| `storage-node/.../HeartbeatSenderService` | Scheduled heartbeat sender (every 5s) |
| `storage-node/.../StorageController` | `POST /storeChunk`, `GET /getChunk/{chunkId}` |
| `storage-node/.../SimulationController` | `POST /simulate/offline`, `POST /simulate/online` |
| `client-ui/...` | React + Vite frontend (upload, download, node status, logs, simulation) |

---

# 11. Technology Stack

## Actual implemented technology stack

- **Java:** Version 17.
- **Spring Boot:** Version 3.5.12 (starter-web, starter-data-jpa, starter-test).
- **Spring Data JPA / Hibernate:** With SQLite via `sqlite-jdbc` and Hibernate community dialects.
- **Maven:** Build tool.
- **REST/HTTP (Spring RestTemplate):** Communication between master, storage nodes, client.
- **Local file system:** Stores chunk contents (`storage_5001`, `storage_5002`, `storage_5003`, etc.).
- **SQLite:** Metadata persistence (`metadata.db`).
- **SHA-256:** Checksum via `java.security.MessageDigest`.
- **React + Vite:** Frontend for upload, download, node status, logs, simulation UI.

## Planned / future technologies

- Docker / Docker Compose (Stage 5, **planned**).
- Consistent hashing for placement (Stage 6, **planned**).

## Example `pom.xml` contents (master-service)

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.12</version>
    <relativePath/>
</parent>
<groupId>com.byteharvest</groupId>
<artifactId>master-service</artifactId>
<version>0.0.1-SNAPSHOT</version>
...
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.xerial</groupId>
        <artifactId>sqlite-jdbc</artifactId>
    </dependency>
    <dependency>
        <groupId>org.hibernate.orm</groupId>
        <artifactId>hibernate-community-dialects</artifactId>
    </dependency>
</dependencies>
```

For storage nodes, only `spring-boot-starter-web` and `spring-boot-starter-test` are used.


---

# 12. Core Concepts and Design Choices

## 12.1 File chunking

A large file is divided into smaller byte sequences. For example, if a file is 10 MiB and the configured block size is 1 MiB, it produces ten blocks. A final block may be smaller than the configured size.

Important design details:

- Use binary mode (`rb` and `wb`) so arbitrary files are supported.
- Assign a unique file ID and block IDs rather than relying only on the local filename.
- Store a sequence index for every block.
- Record the original file size and block size.
- Handle empty files explicitly.
- Reassemble blocks by sequence index, not by alphabetical block-name sorting.
- Use a temporary file and atomic rename when writing blocks, where practical.
- Avoid reading the entire file into memory if large-file support is a goal.

A block ID could be generated from a file ID and sequence number, or as a unique identifier. The ID format must be consistent across the client, master, and storage nodes.

## 12.2 Data distribution and block placement

The placement algorithm chooses which nodes should store a block and its replicas. Placement should consider only nodes that are currently eligible to receive data.

### Round-robin placement

Round-robin assigns successive blocks to successive nodes. It is simple and easy to explain, making it useful for an initial prototype. It does not inherently account for node capacity, load, network topology, or changes in cluster membership.

### Hash-based placement

A basic approach uses a deterministic hash of the block ID to choose a node. A simplistic expression such as `hash(block_id) % node_count` can be unstable in Python across separate processes because Python's built-in string hash may be randomized. For reproducible placement, use a stable digest such as SHA-256 and convert part of the digest into an integer.

For example, conceptually:

```python
digest = sha256(block_id.encode("utf-8")).digest()
index = int.from_bytes(digest[:8], "big") % len(eligible_nodes)
```

This simple modulo-based approach can remap many blocks when the node count changes. It is a baseline, not the same as consistent hashing.

### Consistent hashing

Consistent hashing maps nodes and block identifiers to a hash ring. When a node is added or removed, the aim is to remap only a portion of blocks rather than recomputing placement for the whole cluster. Virtual nodes can help balance the ring. This is a potential enhancement and should only be claimed as implemented if the code contains it and tests demonstrate its behavior.

### Placement considerations

- Do not place two replicas of the same block on the same node.
- Skip unavailable nodes.
- Prefer nodes with sufficient free capacity.
- Avoid concentrating all replicas on one physical machine or failure domain when topology information is available.
- Record the selected locations in metadata.
- Re-evaluate placement when nodes join or fail.
- Make the algorithm deterministic enough for debugging and repeatable tests.

## 12.3 Replication

Replication stores multiple copies of the same logical block on different storage nodes. The **replication factor (RF)** is the target number of copies of each block. A target RF of 3 means three copies are desired; it does not mean the system always has three healthy copies during a failure.

Example:

```text
Block B1
  Replica 1 -> Node 1
  Replica 2 -> Node 2
  Replica 3 -> Node 3
```

If Node 2 fails, only two known healthy copies remain. The system should identify the block as under-replicated and, if another eligible node and a healthy source copy are available, create a replacement replica.

### Possible replication approaches

1. **Client-mediated replication:** The client uploads the same block to each selected node. This is straightforward for a prototype but can send the same data over the client network connection multiple times.
2. **Pipeline replication:** The client sends a block to one node, which forwards it to the next node, and so on. This can reduce client-side transfer work but requires reliable forwarding and acknowledgement logic.
3. **Master-coordinated copying:** The master selects source and destination nodes and instructs a destination to copy from a source. The master coordinates but does not need to transfer the block bytes itself.

Choose one approach for the first implementation and describe it accurately. Do not claim pipeline replication if each replica is independently uploaded by the client.

### Write acknowledgements and metadata correctness

A robust upload should not report success simply because the master selected nodes. It should verify that the required writes succeeded. The implementation must define what counts as successful upload:

- Did all requested replicas acknowledge storage?
- Is partial success allowed?
- Is the file visible before all required replicas are written?
- What happens if the process crashes between storing blocks and saving metadata?

For a simple project, the master can mark an upload complete only after the required block copies have been stored and acknowledged. More advanced transactional behavior is outside the initial scope.

### Replication overhead

If each block is stored RF times, the raw data storage requirement is approximately:

`total stored block bytes ≈ original file bytes × RF`

This is an approximation that excludes metadata, temporary copies, and any incomplete or extra replicas.

## 12.4 Metadata management

Metadata describes files and the physical locations of their blocks. It is distinct from the actual block contents.

A conceptual metadata record could look like:

```json
{
  "files": {
    "file-123": {
      "file_name": "example.bin",
      "file_size": 2500000,
      "block_size": 1048576,
      "blocks": [
        {
          "block_id": "file-123-block-0000",
          "sequence": 0,
          "size": 1048576,
          "sha256": "<actual-digest>",
          "replicas": ["node1", "node2", "node3"]
        },
        {
          "block_id": "file-123-block-0001",
          "sequence": 1,
          "size": 1048576,
          "sha256": "<actual-digest>",
          "replicas": ["node2", "node3", "node1"]
        },
        {
          "block_id": "file-123-block-0002",
          "sequence": 2,
          "size": 402848,
          "sha256": "<actual-digest>",
          "replicas": ["node3", "node1", "node2"]
        }
      ]
    }
  }
}
```

This is an illustrative schema, not actual project data. A real implementation should record actual values and should use a schema compatible with the code.

Useful metadata fields include:

- File ID and original filename.
- Original file size.
- Block size.
- Ordered block IDs.
- Each block's byte size.
- Checksum.
- Replica node IDs.
- Upload or creation timestamp.
- Upload status.
- Node status and last heartbeat time.

A JSON file is convenient for a prototype but needs care: concurrent writes can overwrite each other, and a crash during a write can corrupt the file. If JSON is used, use safe write patterns such as writing a temporary file and replacing the old file atomically. A database is a possible later enhancement.

## 12.5 Heartbeat-based failure detection

Each storage node periodically sends a heartbeat to the master. The master records the last time a valid heartbeat was received.

Conceptual flow:

```text
Node starts
   |
   v
Register with master
   |
   v
Send heartbeat periodically
   |
   v
Master updates last_seen
   |
   v
Heartbeat exceeds timeout?
   | No                  | Yes
   v                     v
Keep node healthy     Mark node suspected/unavailable
                           |
                           v
                 Find under-replicated blocks
```

The heartbeat interval and failure timeout should be configurable. For example, a prototype might send heartbeats every few seconds and use a longer timeout, but the exact values should be selected through testing and documented in the configuration.

A timeout is an imperfect failure detector. A slow process, paused machine, overloaded laptop, or network interruption can also cause missed heartbeats. It is more accurate to describe the node as **suspected unavailable** or **marked failed by the timeout policy** than to claim that the system knows with certainty that the machine has crashed.

## 12.6 Fault tolerance and recovery

Fault tolerance means the system can continue to provide some or all intended functionality despite specified failures. It does not mean every possible failure can be survived.

### Node crash

If a node process stops, its blocks may become unavailable on that node. Other replicas can still serve reads if they are healthy.

### Network interruption

A node may still be running but unable to contact the master or client. A heartbeat timeout can lead to the same operational status as a crash. The design should avoid unsafe duplicate recovery actions when communication returns.

### Block corruption

A checksum mismatch indicates that the bytes read do not match the expected digest. If a healthy replica exists, the system can retrieve a verified copy and replace the corrupt copy. Checksum verification detects corruption; it does not repair it on its own.

### Under-replication

After a node is considered unavailable, the master compares the number of healthy known replicas with the configured target RF. If the number is lower, the replication manager should:

1. Find a healthy source replica.
2. Select an eligible destination node that does not already hold that block.
3. Copy the block.
4. Verify its checksum at the destination.
5. Record the new replica only after the copy is confirmed.
6. Recalculate replica health.

The system must handle the case where no healthy source remains. In that case, the block cannot be recreated by replication alone.

### Master failure

The master is a single coordination point in the initial design. Saving metadata to persistent storage can support restart and metadata reload, but it does not eliminate the single point of failure. High-availability master election and consensus are possible future extensions.

## 12.7 SHA-256 integrity checking

A checksum is a digest calculated from the bytes of a block. For SHA-256, the digest is 256 bits, commonly represented as 64 hexadecimal characters.

Typical process:

1. Calculate SHA-256 when the block is created or accepted.
2. Save the expected digest in metadata.
3. Calculate SHA-256 again when reading or verifying the block.
4. Compare the calculated digest with the expected digest.
5. If the digests differ, mark the block copy as corrupt and try a healthy replica if one exists.

A checksum helps detect accidental corruption. It is not encryption and does not by itself provide authenticity against an attacker who can modify both data and the stored checksum.

## 12.8 Parallel retrieval

Because blocks may be on different nodes, the client can potentially retrieve multiple blocks concurrently. This can improve throughput when network and disk resources permit. However, parallel downloads add complexity around timeouts, retries, memory usage, ordering, and error handling. If implemented, compare sequential and parallel retrieval using the same files and test conditions.

---

# 13. Upload Workflow

A possible workflow is:

1. The client chooses a file.
2. The client creates a unique file ID and divides the file into ordered blocks.
3. The client requests a placement plan from the master.
4. The master checks the registered, eligible storage nodes.
5. The master selects the required nodes for each block and replica.
6. The client sends the blocks to the selected nodes, or follows the chosen replication pipeline.
7. Storage nodes write blocks and return acknowledgements.
8. The system verifies that the required writes succeeded.
9. The master records file metadata, block order, checksums, and confirmed replica locations.
10. The file is marked complete only when the project's defined success condition is satisfied.

**Design point:** If the master records metadata before data has been stored successfully, metadata can point to nonexistent blocks. The implementation should handle partial uploads and failures rather than assuming every request succeeds.

---

# 14. Download Workflow

1. The client requests a file by ID or filename.
2. The master returns the file's block list in the correct sequence and available replica locations.
3. The client selects a healthy replica for each block.
4. The client downloads blocks, sequentially or in parallel if implemented.
5. The client verifies each block checksum.
6. If a replica is missing or corrupt, the client may try another healthy replica.
7. The client writes blocks in sequence order to reconstruct the original file.
8. The client checks the final file size and, where available, compares the full-file digest against a digest captured at upload time.

The final-file checksum is optional but useful for testing end-to-end correctness.

---

# 15. Failure and Recovery Workflow

Example: RF = 3, with replicas of block B1 on Node 1, Node 2, and Node 3.

```text
Initial state:
B1 -> Node 1, Node 2, Node 3
Healthy replica count = 3

Node 2 stops sending heartbeats
        |
        v
Master's timeout policy marks Node 2 unavailable
        |
        v
B1 has two known healthy replicas
        |
        v
Replication manager chooses Node 1 or Node 3 as source
        |
        v
A new eligible node is selected as destination
        |
        v
The block is copied and checksum verified
        |
        v
Metadata records the confirmed replacement replica
        |
        v
B1 -> Node 1, Node 3, Node 4
Healthy replica count returns to 3
```

This recovery succeeds only if at least one valid source replica remains, an eligible destination is available, and the relevant operations complete successfully.

---

# 16. Local Development Plan: One Laptop First

The agreed development approach is to build and test locally before attempting the multi-laptop deployment.

A local cluster can use:

```text
Master Node      http://127.0.0.1:8000
Storage Node 1   http://127.0.0.1:8001
Storage Node 2   http://127.0.0.1:8002
Storage Node 3   http://127.0.0.1:8003
```

Each storage-node process should use a separate directory:

```text
storage/
  node1/
  node2/
  node3/
```

These are separate processes but still share the same physical laptop, operating system, disk subsystem, and network stack. This is useful for functional testing, but it does not prove resilience against physical machine or disk failure. For a node-failure demonstration, stop one node process and observe the system's behavior.

## Suggested development stages

### Stage 1: Minimal API and node registry
- Start the master.
- Start one storage node.
- Register the node.
- Inspect the node list.

### Stage 2: Block storage
- Implement store and get operations.
- Save blocks to the node's own directory.
- Test missing-block behavior.

### Stage 3: Chunking and reconstruction
- Split a file into blocks.
- Store blocks.
- Download and reconstruct the file.
- Compare the output with the original.

### Stage 4: Metadata persistence
- Store file IDs, block sequence, block sizes, checksums, and replica locations.
- Restart the master and confirm that metadata reloads correctly.

### Stage 5: Multiple nodes and placement
- Start three storage-node processes.
- Implement a placement algorithm.
- Confirm blocks are distributed across eligible nodes.

### Stage 6: Replication
- Choose a target RF, such as 3.
- Store the required number of copies.
- Confirm replicas are on distinct nodes.
- Test partial-write failure.

### Stage 7: Heartbeats and failure detection
- Make nodes send heartbeats.
- Add a configurable timeout.
- Stop a node and verify the status transition.

### Stage 8: Re-replication
- Detect under-replicated blocks.
- Copy from a healthy source to an eligible destination.
- Verify checksums and metadata.

### Stage 9: Integrity and failure tests
- Deliberately corrupt a test block.
- Confirm checksum mismatch detection.
- Confirm the system can use another valid replica, if implemented.

### Stage 10: Observability and reporting
- Record node state, block locations, replica count, and recovery events.
- Capture test logs and screenshots.
- Measure actual performance.

### Stage 11: Multi-laptop deployment
- Configure each service with reachable IP addresses and ports.
- Bind services to an appropriate network interface rather than loopback.
- Configure firewalls and network access.
- Test node registration, block transfer, heartbeats, failure, and recovery over the network.

Do not begin the multi-laptop stage until the local workflow is reliable and repeatable.

---

# 17. Multi-Laptop Deployment Plan

The intended future demonstration uses one master and three storage nodes, distributed across available machines. The exact number of physical laptops depends on what is available; the architecture can place the master and a storage node on one machine if needed, although separate machines provide a clearer demonstration.

Illustrative layout:

```text
Laptop A: Master Node
Laptop B: Storage Node 1
Laptop C: Storage Node 2
Laptop D: Storage Node 3
```

Example addresses below are placeholders only:

```text
Master:  192.168.1.10:8000
Node 1:  192.168.1.11:8001
Node 2:  192.168.1.12:8002
Node 3:  192.168.1.13:8003
```

For the real deployment:

- Determine the actual local IP address of each machine.
- Configure each node with the master address.
- Register reachable node addresses, not `localhost`.
- Bind the API server to a network-accessible interface when appropriate.
- Allow only the required ports through the local firewall.
- Ensure the machines can communicate on the same network.
- Keep a distinct storage directory on each node.
- Avoid exposing test services to the public internet.
- Use configuration/environment variables rather than hard-coding addresses.
- Test recovery after an actual node process or machine becomes unreachable.

`localhost` always refers to the current machine. It must not be used as the remote address of a different laptop.

---

# 18. API Design Sketch

The exact endpoints should match the actual code. The following is a suggested API inventory.

## Master API

| Endpoint | Method | Purpose |
|---|---|---|
| `/register_node` | POST | Register a storage node |
| `/heartbeat` | POST | Receive node heartbeat |
| `/nodes` | GET | List registered nodes and their states |
| `/upload_request` | POST | Request file/block placement |
| `/store_metadata` | POST | Record confirmed file/block metadata, if designed this way |
| `/metadata/{file_id}` | GET | Retrieve file metadata |
| `/system_status` | GET | Return a summary of cluster health |
| `/blocks/{block_id}/replicas` | GET | Inspect known replica locations, if implemented |

## Storage-node API

| Endpoint | Method | Purpose |
|---|---|---|
| `/register` or master registration call | POST | Register with master, depending on design |
| `/store_block` | POST | Store a block |
| `/get_block/{block_id}` | GET | Retrieve a block |
| `/delete_block/{block_id}` | DELETE | Delete a block, subject to safety checks |
| `/health` | GET | Local health check |
| `/copy_block` | POST | Accept a replica copy, if this design is used |

Avoid duplicate registration flows: decide whether the node initiates registration or the master registers nodes through configuration. The actual API should have one documented source of truth.

For binary data, use a suitable binary request body or multipart upload rather than putting arbitrary block contents in URL query parameters. URL parameters are not appropriate for large or arbitrary binary files.

---

# 19. Data Structures

## 19.1 Node record

A node record may contain:

```json
{
  "node_id": "node1",
  "host": "127.0.0.1",
  "port": 8001,
  "status": "healthy",
  "last_heartbeat": "<timestamp>",
  "capacity_bytes": null,
  "used_bytes": null
}
```

Values such as capacity and usage are optional until implemented. Timestamps should use a consistent format, ideally UTC for persisted timestamps.

## 19.2 File record

A file record should identify the file and preserve the ordered block list. It should distinguish an upload in progress from a completed upload.

## 19.3 Block record

A block record should include its ID, file association, sequence number, byte size, expected checksum, and replica locations.

## 19.4 Node status

Suggested states:

- `starting`
- `healthy`
- `suspected`
- `unavailable`
- `recovering`

A simple prototype may use fewer states, but the status names and transitions should be consistent.

---

# 20. Enhancements Compared with the 6th Semester

The project presentation described the 6th-semester work as primarily basic distributed file storage. The proposed 7th-semester improvements are:

1. **Chunk-based architecture:** Divide files into smaller blocks rather than treating each file as one indivisible unit.
2. **Replication strategy:** Maintain multiple copies of each block, with a configurable target replication factor.
3. **Heartbeat monitoring:** Add periodic node health messages and timeout-based detection.
4. **Automatic failure recovery:** Detect under-replicated blocks and recreate replicas when a healthy source and destination are available.
5. **SHA-256 integrity verification:** Verify block data against stored digests.
6. **Docker-based deployment:** Containerize services for repeatable deployment, if implemented.
7. **Improved modular architecture:** Separate master, storage node, client, metadata, placement, replication, and monitoring responsibilities.

**Important reporting distinction:** The presentation used phrases such as “planned automatic failure recovery” and listed Docker deployment as an improvement. The final report must distinguish features that are implemented, features currently being developed, and features planned for future work.

---

# 21. Benefits and Expected Contributions

The expected benefits of the proposed system are:

- **Scalability:** Additional nodes can increase aggregate storage capacity, subject to placement and metadata design.
- **Availability:** A healthy replica may serve a block when another replica is unavailable.
- **Fault tolerance:** Replication and recovery can reduce the impact of individual storage-node failures.
- **Integrity checking:** Checksums can identify corrupted block contents.
- **Modularity:** Separate services make the design easier to understand, test, and extend.
- **Educational value:** The system makes distributed storage concepts visible through repeatable experiments.
- **Low-cost experimentation:** The first version can be developed on one laptop without dedicated cluster hardware.

These are design goals, not measured outcomes. Any claim of a specific performance improvement, availability percentage, or recovery time must be backed by test results.

---

# 22. Limitations and Risks

1. **Single master:** The master can become a bottleneck or single point of failure.
2. **Metadata persistence:** JSON is simple but can be unsafe for concurrent writes unless carefully managed.
3. **Heartbeat false positives:** Slow or disconnected nodes may be marked unavailable even if they have not crashed.
4. **Replication overhead:** RF=3 requires approximately three copies of the raw block data.
5. **Network overhead:** Copying replicas consumes bandwidth and can slow uploads or recovery.
6. **Failure-domain limitations:** Three node processes on one laptop do not protect against physical laptop, disk, or power failure.
7. **Partial uploads:** A crash may leave incomplete blocks or metadata unless upload states and cleanup are handled.
8. **Concurrent operations:** Simultaneous uploads, failures, and recovery can cause race conditions without coordination.
9. **Data consistency:** The system must define when an upload is considered complete and how metadata reflects partially completed operations.
10. **Security:** A local prototype may not implement authentication, authorization, encryption, or protection against malicious clients.
11. **Scalability claims:** Testing with three nodes demonstrates a small cluster, not unlimited or production-scale scalability.
12. **No automatic source of lost data:** If all valid replicas of a block are lost, replication cannot reconstruct that block without another source or backup.

---

# 23. Testing and Evaluation Plan

A final report should present measured results from actual tests. Suggested tests include:

## Functional correctness

- Upload a small text file.
- Upload a binary file such as an image or PDF.
- Upload a file larger than one block.
- Download and reconstruct each file.
- Compare original and reconstructed bytes.
- Test empty files and files whose sizes are exact multiples of block size.

## Distribution

- Upload a file that produces several blocks.
- Inspect block locations on each node.
- Confirm that placement uses eligible nodes.
- Confirm that the same block is not assigned twice to the same node as a replica.
- Compare block counts or bytes stored per node.

## Replication

- Confirm the number of replicas for each block.
- Stop one storage-node process.
- Confirm that surviving replicas can still serve a read.
- Confirm that the system identifies under-replicated blocks.
- Confirm that recovery produces a new, verified replica when a destination is available.

## Heartbeat detection

- Record the heartbeat interval and timeout.
- Stop a node process.
- Measure time until the master marks it unavailable.
- Restart the node and observe whether it registers or becomes healthy again.

## Integrity

- Record a block's SHA-256 digest.
- Modify a copy of the block in a controlled test.
- Confirm that verification detects the mismatch.
- If repair is implemented, confirm that a valid replica can replace the corrupt copy.

## Performance

Measure:
- Upload duration.
- Download duration.
- Throughput in MiB/s.
- Recovery duration.
- Storage overhead caused by replication.
- Node storage distribution.
- Sequential versus parallel download, if both are implemented.

For meaningful comparison, use the same test files, block size, replication factor, machine, and test procedure. Repeat measurements and report the mean or median and the number of runs. Include hardware and environment details.

## Suggested results table

Fill this table with actual measurements:

| Test | Input / Configuration | Metric | Measured Result |
|---|---|---|---|
| Upload | File size: ___ MiB; block size: ___ | Upload duration | ___ seconds |
| Download | File size: ___ MiB | Download duration | ___ seconds |
| Integrity | Number of blocks checked: ___ | Corruption detection | ___ |
| Node failure | Heartbeat interval: ___; timeout: ___ | Detection time | ___ seconds |
| Recovery | RF target: ___ | Re-replication duration | ___ seconds |
| Distribution | Number of nodes: ___ | Per-node bytes stored | ___ |
| Replication overhead | RF target: ___ | Stored bytes / original bytes | ___ |
| Reconstruction | File size: ___ | Byte-for-byte match | Pass / Fail |

Do not invent numbers to make the project look stronger. If a test has not been run, leave the result blank or mark it “Not yet tested.”

---

# 24. Suggested Demonstration Scenario

A concise demonstration can show:

1. Start the master and three storage-node processes.
2. Show the node registry and their health status.
3. Upload a file that produces multiple blocks.
4. Show block IDs, block sequence, node placement, and replica counts.
5. Download the file and compare it with the original.
6. Stop one storage-node process.
7. Wait for the configured heartbeat timeout.
8. Show that the master marks the node unavailable and identifies affected blocks.
9. Show the system creating a replacement replica on a healthy node, if recovery is implemented.
10. Download the file again and verify the reconstructed bytes.
11. Optionally corrupt a test block and demonstrate checksum detection.

The demonstration should distinguish between a feature that works in the current code and one that is still planned.

---

# 25. Research Background and Related Systems

The project is conceptually related to distributed file systems and fault-tolerant distributed services.

## Google File System (GFS)

GFS uses a master to manage metadata and chunk servers to store file chunks. Its design discusses large files, chunk replication, heartbeats, and recovery from component failures. The project borrows the general architectural idea of separating coordination/metadata from block storage.

## Hadoop Distributed File System (HDFS)

HDFS uses a NameNode for namespace and block metadata and DataNodes for block storage. Heartbeats and block reports help the NameNode track DataNodes, and the system can re-replicate blocks when replicas become unavailable. The project adapts these general concepts into a smaller prototype.

## Replication and failure detection

Distributed systems literature explains that replication can improve availability but introduces consistency and coordination challenges. Heartbeat timeouts are practical but imperfect: the absence of a heartbeat does not prove that a node has crashed. Recovery policy should account for this uncertainty.

## Placement algorithms

Round-robin is easy to implement, hash-based placement is deterministic when based on a stable hash, and consistent hashing can reduce the number of objects reassigned when membership changes. These algorithms have different complexity and balancing characteristics. The final report should describe the algorithm actually implemented and evaluate it with the project's tests.

**Citation discipline:** Do not treat this overview as a substitute for verified bibliographic references. Verify exact paper titles, authors, venues, years, volume/issue, pages, and DOI or official URL before submitting the final bibliography.

---

# 26. Preliminary References to Verify

The following are relevant, well-known references for the project. Confirm publication metadata from the publisher, conference proceedings, or an academic index before final submission.

1. S. Ghemawat, H. Gobioff, and S.-T. Leung, “The Google File System,” *Proceedings of the 19th ACM Symposium on Operating Systems Principles (SOSP)*, 2003.
2. K. Shvachko, H. Kuang, S. Radia, and R. Chansler, “The Hadoop Distributed File System,” *2010 IEEE 26th Symposium on Mass Storage Systems and Technologies (MSST)*, 2010.
3. G. Coulouris, J. Dollimore, T. Kindberg, and G. Blair, *Distributed Systems: Concepts and Design*, 5th ed., Pearson, 2011.
4. A. S. Tanenbaum and M. van Steen, *Distributed Systems: Principles and Paradigms*, 2nd ed., Pearson Prentice Hall, 2007.

Other papers were previously discussed as possible literature-review sources, including works on replication fault tolerance, distributed file system surveys, metadata management in HDFS, and comparisons of GFS, HDFS, and MinIO. Their bibliographic details should be independently verified before they are cited. Do not include a reference in the final report unless its existence and metadata can be confirmed.

---

# 27. Current Project Status: Verify Before Writing the Final Report

The discussions so far established a design and development roadmap. They did not establish that every planned feature has been implemented, integrated, or measured.

Use the following table as a checklist and update it against the real source code:

| Component / Feature | Status from the planning discussion | What must be checked |
|---|---|---|
| Master API | Proposed | Does the server run and expose working endpoints? |
| Storage-node API | Proposed | Can nodes store and return blocks? |
| Node registration | Proposed | Are nodes registered and tracked correctly? |
| File chunking | Proposed | Does chunking work for binary files and edge cases? |
| Block placement | Basic distribution discussed | Which algorithm is actually in the code? |
| Metadata tracking | Basic in-memory/JSON approach discussed | Does it persist and reload correctly? |
| File upload | Minimal prototype described in earlier planning | Does it work end-to-end with the current repository? |
| File download/reconstruction | Minimal prototype described in earlier planning | Is output byte-for-byte identical to input? |
| Replication factor 3 | Planned enhancement | Are three distinct, confirmed copies created? |
| Heartbeat monitoring | Planned enhancement | Are heartbeats and timeouts implemented and tested? |
| Automatic re-replication | Planned enhancement | Does a failed node trigger verified replica creation? |
| SHA-256 integrity checking | Listed as a semester improvement | Is the checksum calculated, stored, and checked in code? |
| Docker deployment | Listed as a semester improvement | Are Dockerfiles/Compose configuration present and tested? |
| Parallel retrieval | Proposed/optional | Is concurrent download implemented and benchmarked? |
| Multi-laptop deployment | Future deployment stage | Has it been tested across real machines? |
| Monitoring dashboard | Optional | Does it exist in the current code? |
| Consensus / multi-master | Out of initial scope | Do not claim unless later implemented. |

### Critical consistency rule

A design document, slide, or planned feature is not evidence that the feature has been implemented. The final report should use wording such as:

- **Implemented:** The feature exists in the code and passes documented tests.
- **In progress:** Some code exists, but the feature is incomplete or not fully tested.
- **Planned:** The feature is part of the intended design but is not implemented yet.
- **Future work:** The feature is outside the current implementation.

---

# 28. Recommended Academic Report Structure

A report may be organized as follows, depending on the college's official template:

1. Title page
2. Certificate / declaration / acknowledgement, if required by the institution
3. Abstract
4. Table of contents
5. List of figures and tables
6. Chapter 1 — Introduction
   - Overview of the project
   - Need for the application
   - Target users and deployment context
   - Benefits and competitive advantage
   - Technology overview
   - Objectives
7. Chapter 2 — Literature Survey
   - Research papers
   - Problems identified after the literature review
   - Research gap
   - Problem definition
   - Objectives of the study
   - Scope of the study
8. Chapter 3 — System Analysis and Design
   - Existing system and limitations
   - Proposed system
   - Functional and non-functional requirements
   - Architecture diagram
   - Use-case or workflow diagrams
   - Data model and metadata design
9. Chapter 4 — Methodology and Algorithms
   - File chunking
   - Data placement
   - Replication
   - Heartbeat monitoring
   - Failure detection
   - Re-replication
   - Checksum verification
10. Chapter 5 — Implementation
    - Technology stack
    - Repository structure
    - API design
    - Master node
    - Storage nodes
    - Client upload/download
    - Deployment setup
11. Chapter 6 — Testing and Results
    - Test environment
    - Test cases
    - Performance measurements
    - Failure-recovery results
    - Screenshots and observations
12. Chapter 7 — Conclusion and Future Work
13. References
14. Appendices, if required

Follow the official college template if it differs from this suggested structure.

---

# 29. Instructions for Claude: How to Use This Document

Copy the following prompt together with this README when asking Claude to help prepare the report.

---

## Prompt for Claude

You are helping prepare an academic major-project report for the project **“Scalable Distributed Storage System with Replication and Fault Tolerance.”** Use the attached README as the primary source of project context.

### Your tasks

1. Read the entire README before drafting.
2. Preserve the project title, team identity, project purpose, architecture, terminology, and intended development approach.
3. Distinguish carefully between features that are implemented, features in progress, and planned features. The README is partly a design plan and does not prove that all features have been implemented.
4. Do not invent experimental results, benchmark values, screenshots, implementation details, code files, research papers, citations, or performance improvements.
5. Do not claim that the project achieves a specific availability percentage, recovery time, speedup, or percentage improvement unless actual test results are provided.
6. Use GFS and HDFS as related systems and sources of inspiration, but do not claim that this project is a full implementation of either.
7. Explain distributed storage, chunking, block placement, replication, metadata, heartbeat monitoring, fault detection, re-replication, and checksums in clear academic language appropriate for a computer science engineering project report.
8. Keep the report technically consistent. If one section says that the client splits files, another section should not say that the master splits them unless the actual implementation does both.
9. Treat RF=3 as a target replication factor only if it is actually configured and implemented. Explain that a failed node may temporarily reduce the healthy replica count.
10. Verify bibliographic details for research references. Do not cite sources that cannot be verified. Clearly flag any reference that needs confirmation.
11. Use diagrams, tables, algorithms, and pseudocode where useful. Label proposed diagrams and pseudocode accurately; do not present them as screenshots of the actual application.
12. Use formal, clear academic English without unnecessary repetition or exaggerated claims.
13. If the actual code repository, screenshots, test logs, or official report template are supplied later, inspect those materials and prioritize them for implementation facts.
14. Before writing final chapters, produce a list of missing information that is necessary for an accurate report. Do not guess missing values.
15. Once the missing information is supplied, draft the report chapter by chapter and check that the abstract, objectives, architecture, implementation, results, and conclusion agree with one another.

### Required first response

Before writing the full report:
- Summarize your understanding of the project.
- List which facts are known from the README.
- List which implementation details still need verification.
- List the exact files, code, screenshots, test results, or template you need from me.
- Identify any references whose bibliographic details must be verified.

Do not produce fabricated results or silently turn planned features into completed features.

---

# 30. Information to Add Before Final Submission

Complete this checklist as development progresses:

- [ ] Actual repository tree and source code.
- [ ] Final block size and rationale.
- [ ] Actual placement algorithm and code location.
- [ ] Actual replication factor and replication method.
- [ ] Whether data is sent by the client to each replica or copied through a pipeline.
- [ ] Actual metadata schema and persistence method.
- [ ] Actual heartbeat interval and failure timeout.
- [ ] Exact node status states and transitions.
- [ ] Actual re-replication trigger and copy workflow.
- [ ] Actual checksum behavior and corruption test.
- [ ] Actual API endpoint list.
- [ ] Whether parallel download is implemented.
- [ ] Whether Docker is configured and tested.
- [ ] Whether multi-laptop deployment has been completed.
- [ ] Operating system, CPU, RAM, and test environment.
- [ ] Test file sizes and number of test runs.
- [ ] Upload/download timing results.
- [ ] Failure detection and recovery timing results.
- [ ] Screenshots of the working application and logs.
- [ ] Verified references and required citation style.
- [ ] Official college report format and page/format requirements.

---

# 31. Final Note

The core contribution of the project is the integration and demonstration of block-based distributed storage, node coordination, replication, heartbeat-based failure detection, and replica recovery in a modular educational prototype. The quality of the final report will depend on accurately documenting what the system actually does, explaining the algorithms and design trade-offs, and presenting repeatable test results.

**Keep the distinction between intended design and verified implementation throughout the report.** That will make the report technically credible and easier to defend during the project review.

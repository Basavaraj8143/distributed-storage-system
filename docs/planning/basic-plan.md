# 🚀 Scalable Distributed Storage System
### With Replication & Fault Tolerance

---

## 📌 Project Overview

This project implements a simplified distributed storage system inspired by systems like Google File System (GFS) and Hadoop HDFS.

The system splits files into chunks, distributes them across multiple storage nodes, and ensures data availability through replication and automated recovery mechanisms.

---

## 🎯 Objectives

- Design a master-based distributed storage architecture
- Implement chunk-based file partitioning
- Ensure data durability using replication
- Detect node failures using heartbeat monitoring
- Enable automatic recovery through re-replication
- Simulate distributed deployment using Docker

---

## 🏗 System Architecture

- **Master Service**
    - Handles metadata (file → chunks → nodes)
    - Coordinates storage and recovery
    - Monitors node health

- **Storage Nodes**
    - Store actual file chunks
    - Serve chunk requests
    - Send heartbeat signals

- **Client**
    - Upload / Download files
    - (Optional) UI dashboard

---

## 🧱 Development Stages

---

### 🔹 Stage 0 — Setup

- Initialize GitHub repository
- Define folder structure
- Assign team roles
- Setup development environment (Java, IntelliJ, Maven)

---

### 🔹 Stage 1 — Core Storage (V1)

**Goal:** Basic distributed file storage

- File upload API
- Chunk splitting (fixed size)
- Storage node APIs:
    - `/storeChunk`
    - `/getChunk`
- Round-robin chunk distribution
- Metadata tracking (in-memory initially)

**Output:**
- File distributed across nodes
- File can be reconstructed successfully

---

### 🔹 Stage 2 — Replication (V2)

**Goal:** Ensure data redundancy

- Implement replication factor (default = 2)
- Store chunk copies on multiple nodes
- Update metadata to track replicas

**Output:**
- Data remains available even if one node fails

---

### 🔹 Stage 3 — Fault Tolerance (V3)

**Goal:** Self-healing system

- Heartbeat mechanism (node → master)
- Failure detection using timeout
- Identify under-replicated chunks
- Automatic re-replication

**Output:**
- System recovers from node failures automatically

---

### 🔹 Stage 4 — Data Integrity (V3.5)

**Goal:** Detect corrupted data

- Generate SHA-256 hash for each chunk
- Validate during retrieval
- Replace corrupted chunks using replicas

---

### 🔹 Stage 5 — Docker Deployment (V4)

**Goal:** Simulate real distributed environment

- Dockerize master and storage nodes
- Use docker-compose for orchestration
- Simulate failures by stopping containers

---

### 🔹 Stage 6 — Advanced Architecture (V5)

**Goal:** Improve scalability

- Implement consistent hashing
- Map chunks to nodes using hash ring
- Reduce data movement during node changes

---

### 🔹 Stage 7 — UI Dashboard (Optional)

- File upload/download interface
- Node status monitoring
- Chunk distribution visualization

---

## 👥 Team Responsibilities

- **Master Service**
    - Upload API
    - Metadata management
    - Replication logic

- **Storage Nodes**
    - Chunk storage & retrieval
    - Local file handling

- **Client/UI**
    - User interaction
    - API integration

- **DevOps**
    - Docker setup
    - Testing & deployment

---

## 🧪 Testing Strategy

- Upload and download validation
- Node failure simulation
- Recovery verification
- Data integrity checks

---

## ⚠️ Limitations

- Single master (potential bottleneck)
- Not production-scale
- No consensus protocol
- Limited concurrency handling

---

## 🔮 Future Improvements

- Master replication using Raft
- Erasure coding instead of replication
- Rack-aware data placement
- Performance benchmarking

---

## 🎯 Final Demonstration Flow

1. Upload file
2. Show chunk distribution
3. Simulate node failure
4. Detect failure via heartbeat
5. Trigger re-replication
6. Download file successfully

---

## 💡 Key Concepts Demonstrated

- Distributed architecture
- Data partitioning
- Replication strategies
- Fault tolerance
- Self-healing systems
- Consistency trade-offs

---
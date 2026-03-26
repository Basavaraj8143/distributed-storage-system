# 📦 Scalable Distributed Storage System
### With Replication & Fault Tolerance

**Final Year Project – Distributed Systems**
**Academic Year 2026–27**
**Team: Byte-Harvest**

---

## 📌 Project Overview

This project implements a simplified distributed storage system inspired by modern architectures such as Google File System (GFS) and Hadoop Distributed File System (HDFS). The system demonstrates:

- Chunk-based file storage
- Metadata coordination using a centralized Master
- Configurable replication factor
- Heartbeat-based failure detection
- Automatic re-replication (self-healing)
- Docker-based distributed simulation

The objective is to understand the architectural principles behind scalable and fault-tolerant storage systems.

---

## 🏗 Architecture Overview

The system consists of three main components:

### 1️⃣ Master Service

- Manages metadata (file → chunk → node mapping)
- Tracks replication factor
- Monitors node health via heartbeat
- Detects node failures
- Triggers automatic re-replication

### 2️⃣ Storage Nodes

- Store file chunks
- Compute SHA-256 checksum for integrity
- Send periodic heartbeat signals
- Respond to store/retrieve/delete requests

### 3️⃣ Client Interface

- Upload files
- Download files
- View system status
- Trigger failure simulation (for testing)

---

## 🔁 Core Features

- File splitting into fixed-size chunks
- Replication across multiple nodes
- Automatic failure detection via timeout
- Under-replication recovery mechanism
- Data integrity verification using SHA-256
- Docker-based container deployment

---

## 🧠 Key Concepts Implemented

- Distributed Architecture
- Metadata–Data Separation
- Replication Strategy
- Fault Detection
- Self-Healing Systems
- Consistent Hashing *(Planned / Advanced Feature)*
- CAP Theorem Trade-offs

---

## 🗂 Project Structure

```
distributed-storage-system/
│
├── master-service/
├── storage-node/
├── client-ui/
├── docker/
├── docs/
└── README.md
```

---

## 🐳 Deployment

The system is designed to run using Docker containers:

- 1 Master container
- Multiple Storage Node containers
- Shared Docker network

Failure simulation can be performed by stopping a node container.

---

## ⚙️ Technology Stack

| Component | Technology |
|-----------|-----------|
| Backend | Java (Spring Boot) |
| Metadata DB | SQLite |
| Frontend | React *(optional)* |
| Containerization | Docker & Docker Compose |
| Data Integrity | SHA-256 |

---

## 📈 Development Phases

| Phase | Description |
|-------|-------------|
| V1 | Chunk-based storage |
| V2 | Replication mechanism |
| V3 | Fault detection & recovery |
| V4 | Dockerized distributed simulation |
| V5 | Consistent hashing *(advanced enhancement)* |

---

## ⚠️ Current Limitations

- Single Master (potential bottleneck)
- Not production-scale
- No leader election or consensus protocol
- No rack-aware replica placement

---

## 🚀 Future Enhancements

- Master replication using Raft
- Erasure coding instead of replication
- Performance benchmarking
- Rack-aware placement strategy
- Monitoring dashboard

---

## 👥 Team Members

- Basavaraj N
- Akash M K
- Ishan Patil
- Disha H

---

## 📚 Academic Relevance

This project demonstrates practical implementation of distributed storage concepts aligned with real-world systems such as GFS and HDFS, focusing on architectural understanding rather than commercial deployment.
# 📦 Scalable Distributed Storage System

### Chunk-Based Storage · Replication · Fault Tolerance · Self-Healing

![Java](https://img.shields.io/badge/Java-Spring%20Boot-brightgreen?style=flat-square&logo=springboot)
![Docker](https://img.shields.io/badge/Deploy-Docker-2496ED?style=flat-square&logo=docker)
![SQLite](https://img.shields.io/badge/Metadata-SQLite-003B57?style=flat-square&logo=sqlite)
![SHA-256](https://img.shields.io/badge/Integrity-SHA--256-orange?style=flat-square)
![Status](https://img.shields.io/badge/Status-Final%20Year%20Project-blueviolet?style=flat-square)

> Inspired by GFS and HDFS — a ground-up implementation of distributed storage principles including replication, fault detection, and automatic recovery.

**Team:** Byte-Harvest &nbsp;|&nbsp; **Academic Year:** 2026–27 &nbsp;|&nbsp; **Course:** Final Year Project — Distributed Systems

---

## 📌 Overview

This project builds a simplified distributed file storage system from scratch, demonstrating the architectural principles behind production-grade systems like **Google File System (GFS)** and **Hadoop Distributed File System (HDFS)**.

The focus is on understanding, implementing, and simulating:

- **Chunk-based storage** — files split and distributed across nodes
- **Metadata coordination** — centralized Master managing file-to-chunk-to-node mapping
- **Configurable replication** — fault tolerance through redundancy
- **Heartbeat-based failure detection** — automatic identification of dead nodes
- **Self-healing recovery** — under-replicated chunks re-replicated automatically
- **Docker simulation** — multi-node distributed environment on a single machine

---

## 🏗 Architecture

The system has three distinct components that mirror real distributed storage designs:

```
┌─────────────────────────────────────────────┐
│                  CLIENT                     │
│   Upload · Download · Status · Simulate     │
└──────────────────┬──────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────┐
│              MASTER SERVICE                 │
│  Metadata DB · Chunk Map · Health Monitor   │
│  Replication Tracker · Re-replication Logic │
└──────┬─────────────────────┬────────────────┘
       │                     │
       ▼                     ▼
┌─────────────┐       ┌─────────────┐
│  Storage    │  ...  │  Storage    │
│  Node 1     │       │  Node N     │
│  Chunks     │       │  Chunks     │
│  SHA-256    │       │  SHA-256    │
│  Heartbeat  │       │  Heartbeat  │
└─────────────┘       └─────────────┘
```

### Master Service
Manages all metadata — file → chunk → node mappings, replication factor tracking, node health via heartbeat timeouts, and triggering re-replication when nodes go down.

### Storage Nodes
Store individual file chunks, compute SHA-256 checksums for integrity verification, send periodic heartbeats, and serve store/retrieve/delete requests from the Master.

### Client Interface
Handles file upload/download, provides a system status view, and supports failure simulation by dropping nodes — useful for testing recovery behavior.

---

## ⚙️ Technology Stack

| Component | Technology |
|---|---|
| Backend | Java + Spring Boot |
| Metadata Store | SQLite |
| Frontend | React *(optional dashboard)* |
| Containerization | Docker + Docker Compose |
| Data Integrity | SHA-256 checksums |

---

## 🔁 Core Features

- ✅ Fixed-size chunk splitting and distribution
- ✅ Replication across configurable number of nodes
- ✅ Heartbeat-based node failure detection (timeout-driven)
- ✅ Automatic under-replication recovery (self-healing)
- ✅ SHA-256 data integrity verification per chunk
- ✅ Docker-based multi-node deployment simulation

---

## 📈 Development Phases

| Phase | Focus |
|---|---|
| **V1** | Chunk-based file storage baseline |
| **V2** | Replication mechanism across nodes |
| **V3** | Fault detection and automatic recovery |
| **V4** | Dockerized distributed simulation environment |
| **V5** | Consistent hashing *(advanced enhancement — planned)* |

---

## 🗂 Project Structure

```
distributed-storage-system/
│
├── master-service/       # Metadata management, health monitoring, re-replication
├── storage-node/         # Chunk storage, SHA-256, heartbeat sender
├── client-ui/            # Upload, download, status, failure simulation
├── docker/               # Compose config, network setup
├── docs/                 # Architecture diagrams, design notes
└── README.md
```

---

## 🐳 Deployment

The system runs entirely in Docker containers:

- **1 Master container** — coordinates metadata and replication
- **N Storage Node containers** — configurable count
- **Shared Docker network** — simulates distributed node communication

To simulate a node failure, simply stop one storage container. The Master detects the timeout and triggers re-replication of affected chunks.

---

## 🧠 Concepts Implemented

| Concept | Status |
|---|---|
| Metadata–Data Separation | ✅ Core design |
| Chunk-based Storage | ✅ Implemented |
| Replication Strategy | ✅ Implemented |
| Heartbeat Fault Detection | ✅ Implemented |
| Self-Healing Re-replication | ✅ Implemented |
| CAP Theorem Trade-offs | ✅ Analyzed |
| Consistent Hashing | 🔄 Planned (V5) |
| Rack-aware Placement | 🔄 Future scope |

---

## ⚠️ Known Limitations

- **Single Master** — no replication of the master itself; potential single point of failure
- **Not production-scale** — designed for architectural demonstration, not commercial load
- **No consensus protocol** — leader election (e.g., Raft) not implemented
- **No rack-awareness** — replicas may land on logically adjacent nodes

---

## 🚀 Future Enhancements

- Master replication using **Raft consensus**
- **Erasure coding** as an alternative to full replication
- Performance benchmarking under varying load
- Rack-aware replica placement strategy
- Live monitoring dashboard with node health visualization

---

## 👥 Team — Byte-Harvest

| Name | Role |
|---|---|
| Basavaraj N | Architecture, Backend, Integration |
| Akash M K | Storage Node, Docker Setup |
| Ishan Patil | Master Service, Fault Detection |
| Disha H | Client Interface, Documentation |

---

## 📚 Academic Context

> This project is submitted as a Final Year Project for the Computer Science Engineering program at **KLEIT (K.L.E Institute of Technology)**, Academic Year 2026–27.

The implementation focuses on understanding the architectural principles behind distributed storage — replication, fault tolerance, and metadata coordination — as demonstrated in systems like GFS and HDFS, rather than production deployment.

---

<div align="center">

*Built to understand how the systems we rely on actually work — from the ground up.*

</div>

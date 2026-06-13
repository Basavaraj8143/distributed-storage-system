# 🐧 WSL Ubuntu Setup & Run Guide (Java Project)

---

## ⚙️ 1. Install WSL + Ubuntu

Open PowerShell (Admin):

```bash
wsl --install
```

Restart system.

After restart:

* Ubuntu will open
* Set username + password

---

## 🔧 2. Install Required Tools

Inside Ubuntu:

```bash
sudo apt update && sudo apt upgrade -y
sudo apt install openjdk-17-jdk maven git net-tools -y
```

---

## ✅ 3. Verify Installation

```bash
java -version
mvn -version
uname -a
```

---

## 📁 4. Access Windows Files from WSL

Windows path:

```bash
D:\projects\final
```

WSL path:

```bash
/mnt/d/projects/final
```

Go to project:

```bash
cd /mnt/d/projects/final
ls
```

---

## 🔨 5. Build Project

Go inside service folder:

```bash
cd master-service
mvn clean package
```

For other services:

```bash
cd ../storage-node
mvn clean package
```

---

## ▶️ 6. Run JAR Files

### Run Master

```bash
cd /mnt/d/projects/final/master-service
java -jar target/*.jar
```

---

### Run Storage Nodes (multiple)

```bash
cd /mnt/d/projects/final/storage-node

java -jar target/*.jar --server.port=5001 --storage.base-dir=/mnt/d/projects/final/storage_5001 --node.id=node-1

java -jar target/*.jar --server.port=5002 --storage.base-dir=/mnt/d/projects/final/storage_5002 --node.id=node-2

java -jar target/*.jar --server.port=5003 --storage.base-dir=/mnt/d/projects/final/storage_5003 --node.id=node-3
```

---

## ⚙️ 7. Configuration (application.properties)

Keep only common config:

```properties
spring.application.name=storage-node
heartbeat.master-url=http://localhost:8080/heartbeat
```

---

### ❗ Do NOT hardcode

Remove:

```properties
server.port=5001
storage.base-dir=...
```

👉 Pass them via command line instead.

---

## 🔁 8. Rebuild After Changes

Every code/config change requires rebuild:

```bash
mvn clean package
```

---

## ⚠️ 9. Important Rules

* Always run using:

  ```bash
  java -jar
  ```
* Do not use Windows paths (`D:\...`)
* Use WSL paths (`/mnt/d/...`)
* Each node must use:

    * different port
    * different storage directory

---

## 🧪 10. Testing

* Run master + nodes
* Check logs for activity
* Stop a node → verify behavior

---

## 🎯 Final Workflow

1. Write code in IntelliJ (Windows)
2. Build using Maven
3. Run in WSL (Linux)
4. Test multiple instances

---

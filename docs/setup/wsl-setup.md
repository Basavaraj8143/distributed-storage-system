# Run ByteHarvest from WSL

This guide runs the Java services directly from WSL. For the Docker setup, use the [root README](../../README.md).

## Requirements

- WSL 2 with Ubuntu
- Java 17 and Maven 3.9 or newer
- Node.js 24 and npm (for the dashboard)

Install Java and Maven in Ubuntu:

```bash
sudo apt update
sudo apt install openjdk-17-jdk maven
```

Install a supported Node.js LTS release and npm using your preferred Node version manager, then confirm `java -version`, `mvn -version`, `node --version`, and `npm --version`.

## Find the repository

Windows drives appear under `/mnt` in WSL. For example:

```bash
cd /mnt/d/projects/final
```

## Build the backend services

From the repository root:

```bash
cd master-service
mvn -B package
cd ../storage-node
mvn -B package
cd ..
```

## Start the master

In one terminal, from the repository root, provide a local password and a random node token. Use the same node token in every storage-node terminal. Keep these values private.

```bash
export APP_USERNAME=admin
export APP_PASSWORD='choose-a-local-password'
export NODE_TOKEN='replace-with-a-random-token-at-least-16-characters'
cd /mnt/d/projects/final/master-service
mvn spring-boot:run
```

The master listens on port `8080` and uses the default storage-node URLs `localhost:5001` through `localhost:5005`.

## Start storage nodes

Open a separate terminal for each node. Repeat with `SERVER_PORT` set to `5001`, `5002`, `5003`, `5004`, and `5005`:

```bash
export NODE_TOKEN='replace-with-a-random-token-at-least-16-characters'
export SERVER_PORT=5001
export HEARTBEAT_MASTER_URL=http://localhost:8080/heartbeat
export STORAGE_BASE_DIR=/mnt/d/projects/final
cd /mnt/d/projects/final/storage-node
mvn spring-boot:run
```

Each node writes its chunks under a port-specific directory such as `storage_5001/` in the configured base directory.

## Start the dashboard

In another terminal:

```bash
cd /mnt/d/projects/final/client-ui
npm ci
npm run dev
```

Open `http://localhost:5173` and sign in with the master username and password. The dashboard uses `http://localhost:8080` for its API by default. Copy `client-ui/.env.example` to `client-ui/.env` only if you need to override local frontend settings.

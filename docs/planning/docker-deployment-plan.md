# Docker Deployment Plan

## Objective

Provide a repeatable local deployment of ByteHarvest with Docker Compose, so the master, three storage nodes, and dashboard can be started together. Keep SQLite as the metadata database; PostgreSQL is outside the scope of this deployment plan.

## Implementation Status

- Root Compose configuration, Dockerfiles for master/storage-node/frontend, Nginx static serving, and `.dockerignore` are in place.
- Master database path, storage-node heartbeat URL, storage base directory, and master node URLs accept environment overrides.
- Root README includes Compose startup instructions and persistent-volume behavior.
- Full container build and runtime workflow still needs validation.

## Deployment Configuration

- The master listens on port `8080` and currently uses SQLite.
- Storage nodes listen on ports `5001`, `5002`, and `5003`; their ports also identify their storage directories.
- Compose uses service names for internal traffic and the host URL for browser access to the API.
- The React dashboard uses `VITE_API_BASE_URL`, supplied at image build time.

## Target Layout

Create a root `compose.yaml` with these services:

| Service | Container port | Host access | Persistent data |
| --- | --- | --- | --- |
| `master` | `8080` | `localhost:8080` | Named volume for the SQLite database |
| `storage-node-1` | `5001` | Optional `localhost:5001` | Dedicated named volume for node 1 chunks |
| `storage-node-2` | `5002` | Optional `localhost:5002` | Dedicated named volume for node 2 chunks |
| `storage-node-3` | `5003` | Optional `localhost:5003` | Dedicated named volume for node 3 chunks |
| `client-ui` | `80` (or Vite dev port during development) | `localhost:5173` or `localhost:80` | None |

Within Compose, configure the master to use `http://storage-node-1:5001,http://storage-node-2:5002,http://storage-node-3:5003` and nodes to send heartbeats to `http://master:8080/heartbeat`. The browser should call the host-accessible master address, not the Compose-only service name. Mount a named volume at the directory containing `metadata.db` so SQLite metadata persists when the master container is recreated.

## Implementation Phases

### Phase 1: Make Services Container-Ready

1. Externalize master datasource settings, storage-node heartbeat URL and port, storage base directory, and master storage-node URLs.
2. Ensure each storage node uses its own mounted data directory. Do not share a chunk directory between nodes.
3. Ensure Spring services bind to `0.0.0.0` inside containers and accept configuration through environment variables or command-line properties.
4. Configure the frontend API base URL at build time or use a small runtime configuration mechanism; Vite environment variables are embedded during build.

**Exit criteria:** each service can run with deployment-specific settings and does not rely on `localhost` for other containers.

### Phase 2: Add Container Build Files

1. Add a multi-stage Dockerfile for the master that builds the Java application with Maven and runs the packaged JAR on a Java 17 runtime image.
2. Add a multi-stage Dockerfile for storage nodes, parameterized by server port and storage path.
3. Add a frontend Dockerfile that builds the Vite app and serves the static output using a small web server image.
4. Add a root `.dockerignore` to exclude build outputs, `node_modules`, local databases, logs, IDE files, and secrets.
5. Use pinned major or exact base image versions and run containers as non-root where practical.

**Exit criteria:** all three images build reproducibly from the repository.

### Phase 3: Add Docker Compose

1. Define the five application services in the root `compose.yaml` and put them on a private Compose network.
2. Configure the master to use SQLite at a stable container path and mount a named volume for its database directory.
3. Configure each storage node with its unique port, mounted volume, and heartbeat URL to the master.
4. Configure master node URLs to use Compose service names.
5. Add health checks where the application exposes a reliable health endpoint. Use service health conditions only when supported by the Compose implementation.
6. Expose only ports needed by host users; storage-node ports can remain private unless debugging or direct access is needed.
7. Provide a committed `.env.example` for non-secret settings if environment configuration is needed; SQLite requires no database credentials.

**Exit criteria:** `docker compose up --build` brings up the database, master, three nodes, and dashboard without manual startup ordering.

### Phase 4: Data and Operations

1. Persist the SQLite database and each node's chunk data in separate named volumes.
2. Document backup and restore for the SQLite database and node chunk volumes; a metadata-only backup is not sufficient to recover files.
3. Document startup, status, logs, shutdown, and cleanup commands. Explain that `docker compose down -v` deletes persistent data.
4. Use a separate Compose override or profile for development settings, if hot reload is needed.

**Exit criteria:** uploaded data survives container recreation and the documented backup can be restored.

### Phase 5: Validate the Full Cluster

1. Build and start the stack from a clean checkout.
2. Confirm all three nodes register as active and the dashboard can reach the master.
3. Upload and download a file and compare its checksum.
4. Restart the master and confirm metadata remains available.
5. Recreate one storage-node container and confirm its chunk data persists.
6. Exercise node-offline simulation and replica repair.
7. Verify CI builds all images and runs service tests; add a Compose smoke test if CI resources permit.

**Exit criteria:** the full workflow operates and persisted data survives service restarts.

## Migration Order

1. Externalize the SQLite database path and service URLs as needed for containers.
2. Containerize each application and test it independently.
3. Add Compose orchestration and persistent volumes.
4. Update the root README quick start and setup documentation with Docker commands and volume behavior.

SQLite is sufficient for this single-master local demonstration. Keep its database file on a named volume; use one master instance so multiple containers do not write to the same SQLite database concurrently.

## Risks and Mitigations

| Risk | Mitigation |
| --- | --- |
| `localhost` points to the container itself | Use Compose service names for internal URLs and host addresses only for browser/host access. |
| SQLite data is lost with a disposable container | Mount the database directory on a named volume and back it up before destructive cleanup. |
| Node directories are shared or lost | Give each node a separate named volume and keep it across container recreation. |
| Dashboard embeds an unreachable API URL | Configure the browser-facing API URL explicitly and validate from the host browser. |
| Services start before dependencies are ready | Add health checks and make startup dependencies explicit; application retry behavior should also be verified. |
| Local secrets are committed | Ignore `.env` and provide placeholders in `.env.example`. |
| Containers run as root or use oversized images | Use multi-stage builds, slim runtime images, and non-root users where supported. |

## Likely Files to Add or Change

- `compose.yaml`
- `.dockerignore`
- `.env.example` (optional)
- `master-service/Dockerfile`
- `storage-node/Dockerfile`
- `client-ui/Dockerfile`
- `master-service/src/main/resources/application.properties`
- `storage-node/src/main/resources/application.properties`
- `README.md`
- `.github/workflows/ci.yml`

## Acceptance Checklist

- [ ] All application images build from the repository.
- [ ] `docker compose up --build` starts the complete local cluster.
- [ ] Master uses its persistent SQLite volume and all nodes register through Compose networking.
- [ ] Dashboard reaches the master from the host browser.
- [ ] Database and each storage-node volume persist across container recreation.
- [ ] Upload, download, checksum verification, and node repair work in containers.
- [ ] Setup, environment variables, backup, restore, and destructive cleanup are documented.

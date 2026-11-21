# Codebase Map

This is a short orientation to the maintained parts of the repository. For setup and behavior, see the [root README](README.md); for project documentation, see [docs/README.md](docs/README.md).

## Application code

- `master-service/`: Spring Boot master API, authentication, SQLite-backed metadata, node health, chunk placement, and replica repair.
  - `src/main/java/com/byteharvest/master/controller/`: HTTP endpoints.
  - `src/main/java/com/byteharvest/master/service/`: Upload/download, metadata, heartbeat, logging, and repair logic.
  - `src/main/java/com/byteharvest/master/entity/` and `repository/`: JPA metadata model and persistence.
  - `src/main/resources/application.properties`: Defaults and environment-variable mappings.
- `storage-node/`: Spring Boot service that writes and serves chunk files, reports heartbeats, and supports failure simulation.
  - `src/main/java/com/byteharvest/storage_node/controller/`: Chunk and simulation endpoints.
  - `src/main/java/com/byteharvest/storage_node/service/`: Heartbeat sender.
- `client-ui/`: React and Vite operator dashboard.
  - `src/api/`: Authenticated master API client.
  - `src/pages/`: Dashboard, files, upload, node status, logs, and simulation pages.
  - `Dockerfile` and `nginx.conf`: Production frontend image and local proxy configuration.

## Deployment and automation

- `compose.yaml`: Local cluster with one master, five storage nodes, and the dashboard.
- `compose.prod.yaml`: Single-server deployment with Nginx as the public entry point.
- `deploy/`: Production Nginx configuration and local ignore rules for deployment credentials.
- `.github/workflows/ci.yml`: Frontend build, backend tests/package checks, and Docker image builds.
- `.dockerignore` and `.gitignore`: Build context and Git exclusions.

## Documentation

- `docs/api/`: API endpoint reference.
- `docs/deployment/`: Production deployment notes.
- `docs/planning/` and `docs/progress/`: Historical plans and implementation notes.
- `docs/rebuild/`: Current service split, authentication, configuration, and repair notes.
- `docs/setup/`: Local development setup guides.
- `docs/postman/`: Postman collection and local environment template.

## Generated and local data

Maven `target/`, frontend `node_modules/` and `dist/`, local logs, SQLite databases, and `storage_5001/` through `storage_5005/` contain generated or runtime data. They should not be treated as source files or committed. Environment files are local configuration; use the checked-in `.env.example` templates as starting points.

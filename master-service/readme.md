# Master Service

The master is the Spring Boot coordinator for ByteHarvest. It exposes the authenticated file and monitoring API, stores file/chunk/replica metadata in SQLite, receives node heartbeats, and coordinates chunk placement and replica repair.

## Requirements

- Java 17
- Maven 3.9 or newer
- A reachable set of storage nodes
- `APP_PASSWORD` and a shared `NODE_TOKEN` of at least 16 characters

## Run locally

From the repository root, set the credentials and start the application:

```powershell
$env:APP_PASSWORD = "your-local-password"
$env:NODE_TOKEN = "replace-with-a-random-token-at-least-16-chars"
cd master-service
mvn spring-boot:run
```

The API listens on port `8080`. The default node addresses are `localhost:5001` through `localhost:5005`. Configure `STORAGE_NODES` to use another comma-separated list. The UI and API use HTTP Basic authentication with `APP_USERNAME` (default `admin`) and `APP_PASSWORD`; storage node requests use `NODE_TOKEN`.

## Configuration

Main defaults and environment-variable mappings are in [`src/main/resources/application.properties`](src/main/resources/application.properties). Important variables:

| Variable | Default | Purpose |
| --- | --- | --- |
| `APP_USERNAME` | `admin` | User-facing API username |
| `APP_PASSWORD` | Required | User-facing API password |
| `NODE_TOKEN` | Required | Shared token for storage-node communication |
| `STORAGE_NODES` | Ports 5001–5005 | Comma-separated node URLs |
| `STORAGE_REPLICATION_FACTOR` | `2` | Desired replicas per chunk |
| `STORAGE_CHUNK_SIZE_BYTES` | `1048576` | Chunk size for new uploads |
| `SPRING_DATASOURCE_URL` | `jdbc:sqlite:metadata.db` | SQLite connection URL |

The master accepts uploads up to 10 MB by default. Local SQLite databases are runtime data and are ignored by Git. Docker Compose persists container metadata in a named volume.

For the full system overview and API reference, see the [root README](../README.md) and [API documentation](../docs/api/endpoints.md).

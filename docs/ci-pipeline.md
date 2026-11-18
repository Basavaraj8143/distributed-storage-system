# CI Pipeline

GitHub Actions runs the workflow at [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) on pushes to `main` or `master` and on pull requests. It grants the workflow read-only repository contents permission.

## Checks

- **Frontend:** Sets up Node.js 24, installs from `client-ui/package-lock.json` with `npm ci`, and runs `npm run build`.
- **Backend:** Runs for `master-service` and `storage-node` using Java 17 and Maven. Each service runs `mvn test` and packages the application.
- **Containers:** Builds the master, storage-node, and dashboard Docker images. Images are used to verify that each Dockerfile builds; they are not pushed to a registry.

The Java and Node runtimes match the project build configuration. Node 24 is also used by the dashboard Dockerfile.

## Scope

This workflow provides continuous integration. It does not publish images, deploy the application, run full-cluster integration tests, or run browser end-to-end tests. Deployment should be added once a target environment and its credential handling are defined.

## Run the checks locally

```bash
cd client-ui && npm ci && npm run build
cd ../master-service && mvn -B --no-transfer-progress test && mvn -B --no-transfer-progress package -DskipTests
cd ../storage-node && mvn -B --no-transfer-progress test && mvn -B --no-transfer-progress package -DskipTests
cd ..
docker build -t byteharvest-master:ci ./master-service
docker build -t byteharvest-storage-node:ci ./storage-node
docker build --build-arg VITE_API_BASE_URL=http://localhost:8080 -t byteharvest-ui:ci ./client-ui
```

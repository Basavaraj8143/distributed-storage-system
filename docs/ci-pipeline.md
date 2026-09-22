# CI Pipeline

This project uses GitHub Actions for continuous integration.

## Workflow File

- Path: `.github/workflows/ci.yml`
- Workflow name: `CI`

## When It Runs

The pipeline runs automatically on:

- Pushes to `main`
- Pushes to `master`
- Any pull request

## What It Checks

The workflow is split into two parts:

### 1. Frontend Job

Job name: `Frontend Build`

This job runs inside the `client-ui` folder and does the following:

1. Checks out the repository
2. Sets up Node.js `20`
3. Restores NPM cache using `client-ui/package-lock.json`
4. Runs `npm ci`
5. Runs `npm run build`

Purpose:

- Verifies that frontend dependencies install correctly
- Verifies that the React/Vite app builds successfully

## 2. Backend Job

Job name pattern: `Backend Verify (master-service)` and `Backend Verify (storage-node)`

This job uses a matrix so it runs once for each backend service:

- `master-service`
- `storage-node`

For each service, it does the following:

1. Checks out the repository
2. Sets up Java `17` using Temurin
3. Restores Maven cache
4. Prints the Maven version available on the runner
5. Runs `mvn -B --no-transfer-progress test`
6. Runs `mvn -B --no-transfer-progress package -DskipTests`

Purpose:

- Verifies unit and Spring context tests pass
- Verifies each Java service can be packaged successfully

## Why Java 17

The workflow currently uses Java `17` because both backend services compile with `<java.version>17</java.version>`.

## Current Coverage

The pipeline currently checks:

- Frontend install and production build
- Master service tests and packaging
- Storage node tests and packaging

The pipeline does not yet check:

- Docker image builds
- Integration tests across all services together
- UI end-to-end browser tests
- Code formatting or lint rules

## How To Extend It Later

Possible next improvements:

1. Add artifact upload for generated JAR files
2. Add frontend linting if ESLint is introduced
3. Add Docker Compose based integration testing
4. Add deployment workflows for staging or demo environments
5. Add badge status to `README.md`

## Local Commands Equivalent To CI

You can reproduce the same checks locally with:

### Frontend

```bash
cd client-ui
npm ci
npm run build
```

### Master Service

```bash
cd master-service
mvn -B --no-transfer-progress test
mvn -B --no-transfer-progress package -DskipTests
```

### Storage Node

```bash
cd storage-node
mvn -B --no-transfer-progress test
mvn -B --no-transfer-progress package -DskipTests
```

## Summary

This CI setup gives the project a strong baseline for a final year project:

- every major code area is validated automatically
- pull requests can be checked before merging
- basic build failures are caught early

It is intentionally simple, but it is a solid foundation for stronger automation later.

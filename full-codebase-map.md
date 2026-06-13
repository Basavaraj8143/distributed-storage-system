# Full Codebase Map

This file explains what each project-owned file in the repository does.

## Scope

- Covered individually: 96 project files outside bulk-generated folders.
- Summarized as generated/external folders instead of per-file: `.git` (1555 files), `client-ui/node_modules` (2322 files), `client-ui/dist` (3 files), `master-service/target` (25 files), `storage-node/target` (13 files).
- Reason for summarizing those folders: they are Git internals, installed dependencies, or build outputs rather than hand-written project logic.

## Root Files

- `.gitignore`: Repository-wide ignore rules for Java build output, IDE files, logs, local storage folders, and frontend dependency/build caches.
- `README.md`: Main project overview describing the distributed storage system, architecture, features, deployment model, and roadmap.
- `.vscode/settings.json`: Minimal VS Code workspace setting that enables Java null-analysis mode automatically.

## Root IntelliJ Metadata

- `.idea/.gitignore`: Tells IntelliJ which local IDE-only files inside `.idea` should stay untracked.
- `.idea/compiler.xml`: Stores IntelliJ compiler and annotation-processing settings for the `master-service` and `storage-node` Maven modules.
- `.idea/encodings.xml`: Declares UTF-8 encoding for Java source directories.
- `.idea/final.iml`: IntelliJ module descriptor for the root project module.
- `.idea/jarRepositories.xml`: Lists Maven repositories IntelliJ may use when resolving dependencies.
- `.idea/misc.xml`: Root IntelliJ project settings, including linked Maven projects and configured JDK/language level.
- `.idea/modules.xml`: Registers the root IntelliJ module file.
- `.idea/vcs.xml`: Maps the project directories to Git within IntelliJ.
- `.idea/workspace.xml`: User-specific IntelliJ workspace state such as recent run configs, UI choices, tab state, and local preferences.

## Docs

- `docs/README.md`: Index for the reorganized documentation tree and summary of the new folder structure.
- `docs/api/endpoints.md`: Human-readable API reference for the master and storage-node endpoints, request formats, and sample cURL usage.
- `docs/setup/wsl-setup.md`: Step-by-step guide for running the project from WSL Ubuntu.
- `docs/planning/basic-plan.md`: Original project plan covering goals, architecture, staged implementation, and milestone breakdown.
- `docs/planning/project-init-gap-analysis.md`: Early gap-analysis note explaining what Stage 1 had and had not completed yet.
- `docs/planning/frontend-parallel-plan.md`: Plan for splitting frontend work into parallel workstreams for shell/routing, API layer, file flow, and monitoring UI.
- `docs/planning/stage-3-plan.md`: Consolidated Stage 3 checklist showing all heartbeat and repair sub-parts as done.
- `docs/planning/stage-4-plan.md`: Design plan for checksum-based chunk integrity validation and corruption repair.
- `docs/progress/stage-1-done.md`: Completion note for the first storage milestone, including implemented endpoints and end-to-end verification steps.
- `docs/progress/stage-2-done.md`: Completion note for replication support and replica-fallback downloads.
- `docs/progress/stage-3-parts-1-3.md`: Documentation for heartbeat sending, active/failed node tracking, and timeout-based failure detection.
- `docs/progress/stage-3-parts-4-6.md`: Documentation for metadata cleanup, under-replication detection, and re-replication workflow.
- `docs/progress/stage-4-done.md`: Completion note for checksum verification, corruption recovery, and the added integrity test suite.
- `docs/progress/staged-changes-2026-04-28.md`: Log of a frontend-focused work session that wired upload/download, monitoring, and logs to the backend.
- `docs/frontend/workstreams/workspace-a.md`: Empty placeholder file for the frontend shell/routing workstream.
- `docs/frontend/workstreams/workspace-b.md`: Workstream brief for the centralized frontend API client layer.
- `docs/frontend/workstreams/workspace-c.md`: Workstream brief for the upload/download user flow pages.
- `docs/frontend/workstreams/workspace-d.md`: Workstream brief for the monitoring dashboard and node status pages.
- `docs/ci-pipeline.md`: Documentation for the GitHub Actions CI workflow, including triggers, frontend/backend jobs, Java/Node setup, and extension ideas.

## Sample Stored Chunk Files

- `storage_5001/chunk_test123`: A chunk file written into the storage folder for node `5001`; current contents are a small PDF-like test artifact.
- `storage_5002/chunk_a56545a1-47b1-48d3-a95c-24b9cd9adbbb`: A replicated chunk file stored under node `5002`; current contents are PDF bytes.
- `storage_5003/chunk_a56545a1-47b1-48d3-a95c-24b9cd9adbbb`: Another replica of the same chunk stored under node `5003`.

## Frontend: `client-ui`

- `client-ui/package.json`: Frontend manifest with Vite scripts and React/router dependencies.
- `client-ui/package-lock.json`: NPM lockfile pinning exact dependency versions for reproducible installs.
- `client-ui/vite.config.js`: Vite configuration that enables the React plugin and runs the dev server on port `5173`.
- `client-ui/index.html`: HTML entry page that provides the root DOM node for the React app.
- `client-ui/.env`: Frontend runtime configuration for backend base URL, endpoint paths, mock-mode toggle, and artificial mock delay.
- `client-ui/src/main.jsx`: React bootstrap file that mounts the app inside `BrowserRouter` and loads global CSS.
- `client-ui/src/App.jsx`: Top-level route table that sends `/` to `/dashboard` and mounts all app pages inside `AppShell`.
- `client-ui/src/types.js`: JSDoc-only type hints for upload responses, node status objects, and system health objects.
- `client-ui/src/styles.css`: Global stylesheet for the app shell, typography, forms, tables, status pills, logs controls, and responsive mobile sidebar behavior.
- `client-ui/src/api/index.js`: Central API client that handles real vs mock mode, fetch helpers, error normalization, blob downloads, and all frontend HTTP calls.
- `client-ui/src/utils/recentFiles.js`: Local-storage helper for remembering recent uploaded files and reusing their `fileId`s later.
- `client-ui/src/components/AppShell.jsx`: Main layout wrapper that combines the side nav, top bar, content area, and mobile overlay state.
- `client-ui/src/components/SideNav.jsx`: Sidebar navigation with links to dashboard, upload, files, nodes, and logs pages.
- `client-ui/src/components/TopBar.jsx`: Header bar with the app title and mobile menu button.
- `client-ui/src/pages/DashboardPage.jsx`: Polling dashboard page that merges health and node data into active/failed counts plus overall cluster status.
- `client-ui/src/pages/FilesPage.jsx`: Download page that accepts a `fileId`, starts browser downloads, and shows a table of recent uploads.
- `client-ui/src/pages/LogsPage.jsx`: Polling log viewer with level filtering and a live event table.
- `client-ui/src/pages/NodeStatusPage.jsx`: Polling node-health page that normalizes backend node data and displays status, last heartbeat, and node URL.
- `client-ui/src/pages/NotFoundPage.jsx`: Fallback route page shown when a URL is not part of the app.
- `client-ui/src/pages/UploadPage.jsx`: Upload form that submits files to the backend, stores successful uploads in local storage, and shows success/error state.

## Storage Node Service: `storage-node`

- `storage-node/mvnw`: Unix Maven wrapper script for building/running the storage-node service without a global Maven install.
- `storage-node/mvnw.cmd`: Windows Maven wrapper script for the same purpose.
- `storage-node/HELP.md`: Spring Initializr-generated help file plus a note about the package name using `storage_node` instead of `storage-node`.
- `storage-node/.mvn/wrapper/maven-wrapper.properties`: Configures the Maven wrapper version and Maven distribution URL.
- `storage-node/.gitignore`: Ignore rules local to the storage-node module.
- `storage-node/.gitattributes`: Line-ending rules for the Maven wrapper scripts.
- `storage-node/pom.xml`: Maven build file for the storage node; uses Spring Boot 4, Web MVC, and test support.
- `storage-node/src/main/resources/application.properties`: Storage-node configuration for the app name, master heartbeat endpoint, and base directory for chunk files.
- `storage-node/src/main/java/com/byteharvest/storage_node/StorageNodeApplication.java`: Spring Boot entry point that also enables scheduled tasks.
- `storage-node/src/main/java/com/byteharvest/storage_node/config/AppConfig.java`: Registers a `RestTemplate` bean used for heartbeat calls.
- `storage-node/src/main/java/com/byteharvest/storage_node/controller/StorageController.java`: Exposes `/storeChunk` and `/getChunk/{chunkId}` endpoints and maps chunk IDs to files under `storage_<port>/`.
- `storage-node/src/main/java/com/byteharvest/storage_node/service/HeartbeatSenderService.java`: Scheduled service that sends node heartbeats to the master every 5 seconds and derives node IDs from the running port.
- `storage-node/src/test/java/com/byteharvest/storage_node/StorageNodeApplicationTests.java`: Minimal Spring Boot smoke test that checks the storage-node application context loads.

## Master Service: `master-service`

- `master-service/.gitignore`: Ignore rules local to the master-service module.
- `master-service/.gitattributes`: Line-ending rules for the wrapper scripts in this module.
- `master-service/.mvn/wrapper/maven-wrapper.properties`: Maven wrapper configuration for the master service.
- `master-service/mvnw`: Unix Maven wrapper script for the master service.
- `master-service/mvnw.cmd`: Windows Maven wrapper script for the master service.
- `master-service/pom.xml`: Maven build file for the master; uses Spring Boot 3, Web, Data JPA starter, Lombok, and Spring Boot test support.
- `master-service/readme.md`: Module-level copy of the project overview focused on master/storage/client architecture and milestones.
- `master-service/HELP.md`: Spring Initializr-generated reference links and Maven inheritance notes.
- `master-service/src/main/resources/application.properties`: Master configuration for app name, upload size limits, and the configured list of storage-node base URLs.
- `master-service/src/main/java/com/byteharvest/master/MasterServiceApplication.java`: Spring Boot entry point for the master with scheduling enabled and datasource auto-config disabled.
- `master-service/src/main/java/com/byteharvest/master/config/AppConfig.java`: Defines the master `RestTemplate` and enables CORS for the Vite frontend on `http://localhost:5173`.
- `master-service/src/main/java/com/byteharvest/master/model/ChunkMetadata.java`: In-memory metadata model for one chunk: chunk ID, chunk index, replica node URLs, and checksum.
- `master-service/src/main/java/com/byteharvest/master/model/FileMetadata.java`: In-memory metadata model for one uploaded file and its chunk list.
- `master-service/src/main/java/com/byteharvest/master/model/HeartbeatRequest.java`: Request DTO for heartbeat payloads from storage nodes.
- `master-service/src/main/java/com/byteharvest/master/service/ChunkService.java`: Core master logic for splitting uploads into 1 MB chunks, selecting replica nodes, storing metadata, reconstructing downloads, verifying checksums, removing failed replicas, and repairing under-replicated or corrupted chunks.
- `master-service/src/main/java/com/byteharvest/master/service/EventLogService.java`: In-memory event buffer that records recent INFO/WARN/ERROR events for the logs API.
- `master-service/src/main/java/com/byteharvest/master/service/HeartbeatService.java`: Tracks node liveness, last-seen timestamps, node URL mappings, and scheduled failure detection.
- `master-service/src/main/java/com/byteharvest/master/service/ReplicationRepairService.java`: Scheduled repair coordinator that removes dead replica references, repairs corrupt replicas, and restores missing replicas.
- `master-service/src/main/java/com/byteharvest/master/controller/FileController.java`: REST controller for file upload and download endpoints on the master.
- `master-service/src/main/java/com/byteharvest/master/controller/HeartbeatController.java`: REST controller that validates and records incoming heartbeats.
- `master-service/src/main/java/com/byteharvest/master/controller/LogsController.java`: REST controller that exposes recent event logs with level and limit query parameters.
- `master-service/src/main/java/com/byteharvest/master/controller/MonitoringController.java`: REST controller that exposes `/nodes/status` and `/system/health` for the frontend dashboard.
- `master-service/src/test/java/com/byteharvest/master/MasterServiceApplicationTests.java`: Minimal Spring Boot smoke test that checks the master application context loads.
- `master-service/src/test/java/com/byteharvest/master/ChunkIntegrityTest.java`: Behavior-focused test suite for checksum generation, corrupt-replica fallback, overwrite repair, and scheduled integrity repair.

## Master IntelliJ Metadata

- `master-service/.idea/.gitignore`: IntelliJ ignore file for module-local IDE state.
- `master-service/.idea/compiler.xml`: IntelliJ compiler and Lombok annotation-processing settings for the master module.
- `master-service/.idea/encodings.xml`: UTF-8 source encoding setting for the master module.
- `master-service/.idea/jarRepositories.xml`: IntelliJ repository list for Maven dependency resolution.
- `master-service/.idea/misc.xml`: IntelliJ project settings for linked Maven files and the configured JDK.
- `master-service/.idea/vcs.xml`: IntelliJ Git mapping for the `master-service` folder.
- `master-service/.idea/workspace.xml`: User-specific IntelliJ workspace state for the master module, including changelist, run config, and local IDE preferences.

## Quick System Reading

- The real application logic lives mainly in `master-service/src/main/java/...` and `storage-node/src/main/java/...`.
- `client-ui/src/...` is a thin operator console over those APIs.
- `docs/...` now contains the project’s planning, progress notes, setup guides, API reference, and CI documentation in one place.
- The `storage_5001`, `storage_5002`, and `storage_5003` folders are runtime data folders created by the storage-node service based on port.

# Progress Log: 2026-08-04

## Persistent Metadata Implementation
- Migrated `master-service` metadata storage from an in-memory `HashMap` to a persistent SQLite database.
- Added `sqlite-jdbc` and `hibernate-community-dialects` dependencies.
- Created `FileMetadataEntity`, `ChunkMetadataEntity`, and `ChunkReplicaEntity` for relational mapping.
- Created Spring Data JPA repositories: `FileMetadataRepository`, `ChunkMetadataRepository`, and `ChunkReplicaRepository`.
- Refactored `ChunkService.java` to use the repositories with `@Transactional` boundaries, ensuring uploads, downloads, and repair operations safely persist changes.
- Completely rewrote `ChunkIntegrityTest.java` to use Mockito with mock JPA behavior, ensuring the unit tests accurately reflect database-driven logic.

## Cluster Node Expansion
- Expanded the default storage cluster configuration from 3 nodes to 5 nodes.
- Added ports 5004 and 5005 to `application.properties` and the `ChunkService` fallback list.
- Updated unit test configuration and the frontend mock API (`client-ui/src/api/index.js`) to recognize all 5 nodes.
- Fixed a minor syntax typo in `FileController.java`.

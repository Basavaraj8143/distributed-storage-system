# Persistent Metadata Storage Plan

## Objective

Add persistent metadata storage to the `master-service` so uploaded file metadata survives application restarts.

Right now the master keeps metadata only in memory:

- `fileId -> List<ChunkMetadata>`

That works for demos during one runtime session, but all metadata is lost if the master restarts.

## Current Problem

The current implementation in `ChunkService` uses:

```java
private Map<String, List<ChunkMetadata>> storage = new HashMap<>();
```

This causes the following limitations:

- uploaded files cannot be downloaded after master restart
- chunk-to-node mappings are not durable
- repair logic depends on runtime-only state
- the system is harder to present as a complete storage platform

## Target Outcome

After this change, the master should:

- store file metadata persistently
- store chunk metadata persistently
- reload metadata automatically on restart
- continue using the same upload, download, heartbeat, and repair APIs

The external API should remain unchanged.

## Recommended Approach

Use a relational database with Spring Data JPA in `master-service`.

Best practical options:

1. `SQLite`
   - very simple for a final year project
   - local file-based database
   - easy to demo

2. `PostgreSQL`
   - stronger long-term option
   - better if Docker/database deployment is added later

## Recommendation

For the current stage, use `SQLite` first.

Reason:

- the README already mentions SQLite
- it keeps setup simple
- it is enough for metadata storage needs
- it improves the project a lot without adding too much infrastructure

## Data Model Plan

Split metadata into durable entities.

### 1. File Entity

Suggested fields:

- `id` or database primary key
- `fileId` (UUID used by APIs)
- `originalFilename` (optional but useful)
- `createdAt`

Purpose:

- represents one uploaded file

### 2. Chunk Entity

Suggested fields:

- `id` or database primary key
- `chunkId`
- `chunkIndex`
- `checksum`
- `fileId` or relation to file entity

Purpose:

- represents one logical chunk of a file

### 3. Chunk Replica Entity

Suggested fields:

- `id`
- `chunkId` or relation to chunk entity
- `nodeUrl`

Purpose:

- stores replica locations separately instead of embedding `List<String>` directly inside a single table row

## Suggested Table Relationship

```text
FileMetadataEntity
  1 -> many ChunkMetadataEntity

ChunkMetadataEntity
  1 -> many ChunkReplicaEntity
```

This maps well to the current design:

- one file has many chunks
- one chunk has many replica node locations

## Service Refactor Plan

The main refactor should happen in `master-service/src/main/java/com/byteharvest/master/service/ChunkService.java`.

### Current State

- upload writes metadata into a `HashMap`
- download reads metadata from the same `HashMap`
- repair logic updates in-memory chunk node lists

### Future State

- upload saves file, chunk, and replica metadata through repositories
- download loads file/chunk/replica metadata from database
- repair logic updates replica rows in database
- corruption repair updates persistent metadata too

## Implementation Phases

### Phase 1. Enable Database Configuration

Tasks:

1. Add proper database dependency for SQLite
2. Keep `spring-boot-starter-data-jpa`
3. Update `application.properties` with datasource settings
4. Remove `DataSourceAutoConfiguration` exclusion from `MasterServiceApplication`

Expected result:

- Spring Boot starts with a real datasource

### Phase 2. Create Persistence Entities

Tasks:

1. Create `FileMetadataEntity`
2. Create `ChunkMetadataEntity`
3. Create `ChunkReplicaEntity`
4. Add JPA relationships and indexes where helpful

Expected result:

- database schema can represent current in-memory metadata

### Phase 3. Create Repository Layer

Tasks:

1. Create repository for files
2. Create repository for chunks
3. Create repository for chunk replicas

Expected result:

- metadata can be queried and updated through Spring Data repositories

### Phase 4. Replace In-Memory Storage

Tasks:

1. Remove or phase out:

```java
private Map<String, List<ChunkMetadata>> storage = new HashMap<>();
```

2. Refactor upload flow to persist metadata
3. Refactor download flow to load metadata from database
4. Refactor repair methods to operate on persistent records

Expected result:

- metadata survives restart

### Phase 5. Keep API Contracts Stable

Tasks:

1. Ensure `/upload` still returns `fileId`
2. Ensure `/download/{fileId}` still works the same
3. Ensure monitoring and repair behavior remains unchanged externally

Expected result:

- frontend does not need major changes

### Phase 6. Add Verification

Tasks:

1. Upload a file
2. Restart master
3. Download using same `fileId`
4. Verify replica metadata still exists
5. Verify repair logic still works after restart

Expected result:

- persistent metadata proven end-to-end

## Files Likely To Change

### Existing Files

- `master-service/pom.xml`
- `master-service/src/main/resources/application.properties`
- `master-service/src/main/java/com/byteharvest/master/MasterServiceApplication.java`
- `master-service/src/main/java/com/byteharvest/master/service/ChunkService.java`

### New Files Likely Needed

- `master-service/src/main/java/com/byteharvest/master/entity/FileMetadataEntity.java`
- `master-service/src/main/java/com/byteharvest/master/entity/ChunkMetadataEntity.java`
- `master-service/src/main/java/com/byteharvest/master/entity/ChunkReplicaEntity.java`
- `master-service/src/main/java/com/byteharvest/master/repository/FileMetadataRepository.java`
- `master-service/src/main/java/com/byteharvest/master/repository/ChunkMetadataRepository.java`
- `master-service/src/main/java/com/byteharvest/master/repository/ChunkReplicaRepository.java`

## Migration Strategy

To keep risk low, use this order:

1. introduce entities and repositories
2. keep current models for API/service-facing logic
3. add mapping between entity objects and current metadata models
4. switch `ChunkService` to repositories
5. remove old in-memory-only storage after verification

This avoids rewriting everything at once.

## Testing Plan

### Unit Tests

- verify metadata save on upload
- verify metadata load on download
- verify replica removal persists
- verify corruption repair persists

### Integration Tests

- start master with test database
- upload file
- restart context
- confirm metadata still available
- confirm download still reconstructs correctly

## Risks

### 1. JPA Mapping Complexity

The current model uses `List<String> nodeUrls`, which does not map cleanly to a normalized table without an extra entity or collection mapping.

Mitigation:

- use a dedicated `ChunkReplicaEntity`

### 2. Refactor Size

`ChunkService` already handles upload, download, integrity repair, and replication repair.

Mitigation:

- refactor in phases
- avoid changing public controller contracts

### 3. SQLite + JPA Setup

SQLite is simple, but JPA integration may need small dependency/config adjustments.

Mitigation:

- start with a minimal local setup and validate schema creation early

## Success Criteria

This feature is complete when:

- metadata is no longer stored only in RAM
- restarting `master-service` does not lose uploaded file metadata
- `download/{fileId}` still works after restart
- replication and corruption repair still update metadata correctly
- frontend works without API contract changes

## Recommended Final Position In Report

Once implemented, you can claim:

- persistent metadata management added to master
- improved system durability
- reduced limitation of in-memory-only coordination

That would significantly strengthen the project quality even before Docker is added.

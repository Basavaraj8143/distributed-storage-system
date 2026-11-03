# PostgreSQL Migration Plan

## Objective

Replace SQLite with PostgreSQL as the metadata database for `master-service`, while preserving uploaded-file metadata and keeping the existing HTTP API and storage-node data unchanged.

## Current State

- `master-service` uses Spring Data JPA repositories and three metadata entities.
- The datasource is configured directly in `master-service/src/main/resources/application.properties` with `jdbc:sqlite:metadata.db`.
- `master-service/pom.xml` includes the SQLite JDBC driver and Hibernate community dialects.
- Hibernate currently manages schema changes with `spring.jpa.hibernate.ddl-auto=update`.
- Existing metadata lives in `master-service/metadata.db`; chunks themselves live on storage nodes.

## Target State

- PostgreSQL provides the master metadata database.
- Connection URL, username, and password are supplied through environment variables or an untracked local configuration file.
- Database schema changes are versioned and repeatable, preferably with Flyway migrations; Hibernate validates the schema instead of modifying it at startup.
- Existing SQLite metadata can be migrated and verified before cutover.
- Upload, download, monitoring, replication repair, and integrity repair keep their current API behavior.

## Decisions and Assumptions

1. PostgreSQL is the only database used in the final runtime configuration.
2. SQLite remains available as the source for a one-time metadata migration, not as a runtime fallback.
3. Metadata is the only data being migrated. Chunk files remain on storage nodes and must not be deleted or rewritten.
4. Use a local PostgreSQL instance for development and CI; Docker Compose can be added as a separate deployment task if desired.
5. Adopt Flyway for explicit schema versioning before production-like data migration. Do not combine automatic schema updates with Flyway.

## Implementation Phases

### Phase 1: Inspect and Record the Existing Database

1. Identify the SQLite database actually used by the running service and confirm its path. Relative JDBC paths depend on the process working directory.
2. Inspect tables, columns, constraints, indexes, row counts, and representative records in `metadata.db`.
3. Compare the database schema with `FileMetadataEntity`, `ChunkMetadataEntity`, and `ChunkReplicaEntity`.
4. Make a separate, timestamped backup of the SQLite database and record its checksum.
5. Record a baseline of file, chunk, and replica counts for post-migration comparison.

**Exit criteria:** source database and schema are understood, backed up, and measurable.

### Phase 2: Prepare PostgreSQL Support

1. Add the PostgreSQL JDBC driver to `master-service/pom.xml` and remove SQLite-only dependencies once the migration utility no longer needs them.
2. Configure the datasource using environment-backed properties, for example `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD`.
3. Set the Hibernate dialect only if required by the selected Spring Boot/Hibernate version; otherwise allow detection from the JDBC connection.
4. Set `spring.jpa.hibernate.ddl-auto=validate` after applying the initial schema migration.
5. Add Flyway and create an initial migration matching the current entities, including primary keys, foreign keys, uniqueness rules, and indexes.
6. Keep credentials out of source control; document safe local setup and add an example environment file with placeholders only.

**Exit criteria:** an empty PostgreSQL database starts the master and Flyway creates a schema that Hibernate validates.

### Phase 3: Validate Application Behavior Against PostgreSQL

1. Run the master service against PostgreSQL with an empty database.
2. Exercise file upload, download, node status, heartbeat, replica repair, and corruption repair.
3. Confirm entity relationships and generated identifiers behave correctly under PostgreSQL.
4. Review transaction boundaries for upload and repair flows so partial metadata writes do not leave inconsistent records.
5. Run the existing master-service test suite against PostgreSQL or a PostgreSQL-compatible test setup.

**Exit criteria:** supported workflows pass on PostgreSQL before real data is imported.

### Phase 4: Migrate SQLite Metadata

1. Stop the master service and storage-node activity that could change metadata during the migration window.
2. Take a fresh SQLite backup and capture final source row counts.
3. Prefer a one-time migration utility or controlled export/import that maps the existing tables to the PostgreSQL schema. Preserve file IDs, chunk IDs/order, checksums, replica node URLs, and timestamps.
4. Import in dependency order: files, chunks, then replicas. Preserve explicit primary keys where applicable and reset PostgreSQL sequences afterward if the schema uses generated numeric keys.
5. Use transactions or restartable batches; record failures and avoid silently skipping invalid rows.
6. Keep the original SQLite database and backup unchanged until the acceptance checks pass.

**Exit criteria:** import completes without rejected rows and source/target counts match.

### Phase 5: Verify and Cut Over

1. Compare row counts for every metadata table and check foreign-key consistency.
2. Compare representative records and all file/chunk/replica relationships; verify chunk checksums against stored replica data where practical.
3. Start the master against PostgreSQL and download previously uploaded files by their original `fileId` values.
4. Verify monitoring and repair operations against migrated records.
5. Confirm a new upload, master restart, and subsequent download work on PostgreSQL.
6. Declare PostgreSQL the active metadata store only after all checks pass.

**Exit criteria:** old and new records remain accessible, integrity checks pass, and normal operations continue after restart.

### Phase 6: Documentation and Cleanup

1. Update root and master-service README configuration and quick-start instructions.
2. Update CI to provision PostgreSQL and supply test connection settings.
3. Remove SQLite runtime configuration, driver, dialect, and obsolete setup instructions.
4. Keep the source SQLite file and migration notes as an archived recovery artifact according to the team's retention decision; do not commit database credentials or unnecessary data copies.

## Rollback Plan

- Before cutover, stop the PostgreSQL-backed master and point the application back to the preserved SQLite configuration/database.
- During cutover, treat PostgreSQL as read-only until acceptance checks pass where feasible. If new writes have occurred on PostgreSQL, do not simply switch back to SQLite; first reconcile or re-export those writes to avoid losing metadata.
- Preserve the original SQLite database and its checksum until the PostgreSQL deployment has been accepted.

## Risks and Mitigations

| Risk | Mitigation |
| --- | --- |
| SQLite and PostgreSQL type or constraint differences | Inspect actual DDL and entity annotations; make the Flyway schema explicit and validate with Hibernate. |
| Existing SQLite file path differs from the expected path | Resolve the datasource path from the real process working directory before backup/export. |
| Partial migration creates orphaned metadata | Import in dependency order, enforce foreign keys, use transactions, and compare relationship counts. |
| PostgreSQL credentials leak into the repository | Use environment variables and commit only placeholder examples. |
| Chunk metadata migrates but chunk files are unavailable | Keep storage nodes and chunk directories intact; verify representative downloads before cutover. |
| Hibernate update mode masks missing migration steps | Switch to Flyway-managed schema and Hibernate `validate` before importing production-like data. |

## Acceptance Checklist

- [ ] Master connects to PostgreSQL using externalized configuration.
- [ ] Flyway creates the schema and Hibernate validates it.
- [ ] Existing SQLite file, chunk, and replica metadata is imported without loss.
- [ ] Foreign keys and source/target table counts match.
- [ ] Existing files download after migration with matching checksums.
- [ ] New upload and download work across a master restart.
- [ ] Heartbeat, monitoring, replication repair, and corruption repair work.
- [ ] CI runs master-service tests with PostgreSQL.
- [ ] README and setup documentation describe PostgreSQL configuration.
- [ ] SQLite source and backup remain available until acceptance is signed off.

## Likely Files to Change During Implementation

- `master-service/pom.xml`
- `master-service/src/main/resources/application.properties`
- `master-service/src/main/java/com/byteharvest/master/entity/`
- `master-service/src/test/`
- `.github/workflows/ci.yml`
- `README.md`
- `master-service/readme.md`
- `docs/setup/`

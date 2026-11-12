# Configurable Replication

How the replication factor, chunk size and node list moved out of hard-coded values and into validated configuration.

Source: `StorageProperties`, `AppConfig`, `UploadService`, `FileManagementService`, `ChunkRepairService` in `master-service`. (`ChunkService`, which this document originally describes, has since been split; see `service-split.md`.)

## 1. Why

`ChunkService` used to hold `REPLICATION_FACTOR = 2` as a constant, read the chunk size and node list through `@Value`, and received its collaborators through field `@Autowired`. That meant:

- Changing the factor needed a code change and a rebuild.
- Nothing stopped a factor larger than the number of nodes. Uploads would simply fail later.
- The class could not be built in a test without Spring.

## 2. What changed

| Before | After |
| --- | --- |
| `static final int REPLICATION_FACTOR = 2` | `storage.replication-factor` (default 2) |
| `@Value` chunk size | `storage.chunk-size-bytes` (default 1048576) |
| `@Value` node list plus `@PostConstruct initNodes` | `storage.nodes`, read through the same properties object |
| Field `@Autowired` | One constructor taking every dependency |

All three settings live in one record, `StorageProperties`, bound with `@ConfigurationProperties(prefix = "storage")`. `AppConfig` registers it with `@EnableConfigurationProperties(StorageProperties.class)`.

`ChunkService` now takes: `StorageProperties`, `RestTemplate`, `EventLogService`, `HeartbeatService`, `FileMetadataRepository`, `ChunkMetadataRepository`.

## 3. Settings

| Property | Environment variable | Default | Meaning |
| --- | --- | --- | --- |
| `storage.replication-factor` | `STORAGE_REPLICATION_FACTOR` | 2 | Copies kept of every chunk |
| `storage.chunk-size-bytes` | `STORAGE_CHUNK_SIZE_BYTES` | 1048576 | Size of each chunk at upload |
| `storage.nodes` | `STORAGE_NODES` | the configured node URLs | Nodes the master may use |

`compose.yaml` sets the first two on the master. `application.properties` maps the environment variables with defaults.

## 4. Validation (fail fast)

`StorageProperties` checks its values in the record constructor. A bad value stops the master at startup with a message naming the property.

| Rule | Reason |
| --- | --- |
| Replication factor at least 1 | Zero copies stores nothing |
| Chunk size at least 1024 bytes | Tiny chunks create huge metadata |
| Node list not empty | Nowhere to store data |
| Replication factor not above the number of nodes | The factor could never be met |

The node list is trimmed, blank entries are removed and duplicates are dropped before the last rule is checked, so a repeated URL does not count as two nodes.

There is no validation starter on the classpath, so the checks are plain code in the constructor rather than annotations.

## 5. Changing the factor on existing data

| Change | Result |
| --- | --- |
| Raised (2 to 3) | Repair sees every chunk as under-replicated and adds the missing copies in one burst |
| Lowered (3 to 2) | Nothing happens. Repair only adds copies, so existing chunks keep the extra one |
| Chunk size changed | Applies to new uploads only. Stored files keep their original chunks |

A trim step that removes surplus copies does not exist yet.

## 6. Tests

`ChunkService` no longer needs Spring in tests. Each test builds it directly with the helper `serviceWith(replicationFactor, chunkSize)`.

- `StoragePropertiesTest`: the validation rules above, plus node list cleanup.
- `ChunkIntegrityTest`: upload honours a different factor and chunk size, upload fails when fewer nodes are active than the factor, and raising the factor makes repair add the missing copy.
- `FileManagementServiceTest`, `NodeReconcileTest`: switched from `@InjectMocks` to manual construction.

These were written but not run when this document was made. Run `mvn test` in `master-service`.

## 7. Known limitations

- **Global only.** One factor for every file. Per-file replication is not implemented.
- **No trim step** when the factor is lowered.
- **Restart required** to change a value. Nothing is reloaded at runtime.
- **`NodeChunk.java` is unused** and can be deleted.

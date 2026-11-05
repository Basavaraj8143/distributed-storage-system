# Kafka Integration Plan

## Objective

Introduce Kafka as an asynchronous event and work queue for ByteHarvest while keeping the current REST APIs, SQLite metadata, and direct chunk transfers in place. Kafka is an optional architecture extension, not a prerequisite for the current local deployment.

## Recommended Scope

Start with **replica repair jobs**. When the master detects an under-replicated chunk, publish a repair command; a consumer processes it and records the result. This demonstrates asynchronous processing and reduces repair work inside heartbeat request flows.

Do not initially route chunk bytes or periodic heartbeats through Kafka. Keep storage-node chunk reads/writes and node health heartbeats on their current HTTP paths. Emit cluster events to Kafka only if there is a clear consumer, such as a future audit or monitoring component.

## Proposed Flow

```text
Master detects repair need
        |
        v
Kafka topic: replica-repair-commands
        |
        v
Master repair worker consumes command
        |
        +--> reads a healthy replica over HTTP
        +--> writes replacement replica over HTTP
        +--> updates SQLite metadata
        +--> publishes success/failure event
```

The master can initially contain both producer and consumer code. A separate worker service can be considered later if independent scaling becomes a goal.

## Implementation Phases

### Phase 1: Define Message Contracts

1. Define a versioned repair command containing an event ID, chunk ID, source replica/node, target node, creation time, and retry count.
2. Define a result event with command ID, outcome, timestamp, and failure reason when applicable.
3. Avoid including chunk payloads; Kafka messages should carry metadata and references only.
4. Define idempotency behavior so receiving the same command more than once cannot create duplicate replica metadata or corrupt repair state.

**Exit criteria:** schemas and duplicate-processing behavior are documented.

### Phase 2: Add Kafka to Local Compose

1. Add a Kafka broker service to `compose.yaml` using a supported single-node development configuration and a persistent named volume if broker state is retained.
2. Configure the master with an internal Compose bootstrap address and make startup resilient to broker readiness delays.
3. Keep Kafka ports private by default; expose them only when local Kafka tooling needs access.
4. Document the additional memory use and basic start/stop/log commands.

**Exit criteria:** Kafka starts locally and the master can connect after broker restarts.

### Phase 3: Add Producer and Consumer

1. Add the Spring Kafka dependency to `master-service`.
2. Publish repair commands only after the master identifies a valid repair opportunity.
3. Consume commands asynchronously and perform the existing HTTP chunk transfer and metadata update steps.
4. Commit offsets only after successful processing or durable handling of the failure.
5. Use bounded retries with backoff; route exhausted commands to a dead-letter topic and expose the failure in application logs.
6. Ensure repair workers can safely retry commands after a restart.

**Exit criteria:** a queued repair command completes successfully and duplicate delivery is safe.

### Phase 4: Failure Handling and Observability

1. Record command lifecycle states or events so queued, processing, completed, and failed work can be diagnosed.
2. Make Kafka unavailability visible and prevent silent loss of repair requests.
3. Decide whether repair requests need a database-backed outbox. Add one if a metadata change and Kafka publish must be atomic; otherwise document and handle the gap between database commit and message publish.
4. Add metrics or logs for queue delay, retries, dead-letter count, and processing duration.

**Exit criteria:** broker outage, consumer restart, duplicate delivery, and exhausted retries have defined outcomes.

### Phase 5: Validate and Document

1. Verify normal upload/download still uses the existing storage path.
2. Trigger under-replication and verify repair is queued, processed, and reflected in SQLite metadata.
3. Restart the master and broker during queued work and verify messages are not silently lost.
4. Test duplicate commands, unavailable source/target nodes, retry exhaustion, and dead-letter handling.
5. Update setup documentation and the architecture diagram to identify which operations use Kafka.

**Exit criteria:** asynchronous repair works across restart and failure scenarios without changing public file APIs.

## Risks and Mitigations

| Risk | Mitigation |
| --- | --- |
| More operational complexity for a small local system | Keep Kafka optional in a Compose profile and retain the current direct mode until the Kafka path is proven. |
| Duplicate delivery causes duplicate or inconsistent repairs | Make command processing idempotent and use stable event IDs. |
| Metadata commits but message publish fails | Consider a transactional outbox; do not assume a database transaction includes Kafka. |
| Broker outage blocks repairs | Retain retryable work durably and report broker/consumer health. |
| Kafka is used for large chunk payloads | Send only IDs and metadata; transfer chunk content directly between services. |

## Likely Files to Change

- `compose.yaml`
- `master-service/pom.xml`
- `master-service/src/main/resources/application.properties`
- `master-service/src/main/java/com/byteharvest/master/service/ReplicationRepairService.java`
- New Kafka producer, consumer, configuration, message, and retry/dead-letter classes under `master-service/src/main/java/com/byteharvest/master/`
- Master service tests and local setup documentation

## Acceptance Checklist

- [ ] Kafka can be started as an optional local Compose service.
- [ ] Repair commands have versioned schemas and stable IDs.
- [ ] Master queues and processes repair jobs asynchronously.
- [ ] Duplicate messages and worker restarts do not corrupt replica metadata.
- [ ] Retries and dead-letter behavior are visible and bounded.
- [ ] Kafka never carries file chunk bytes.
- [ ] Existing upload/download APIs and SQLite remain unchanged.
- [ ] Broker outage behavior and recovery are documented.

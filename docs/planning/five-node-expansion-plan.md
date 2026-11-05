# Five-Node Cluster Expansion Plan

## Objective

Expand the local ByteHarvest cluster from three storage nodes to five, with node services on ports `5001` through `5005`. This is a capacity and resilience demonstration; it does not change the master API or require changes to `FileController`.

## Why Increase the Node Count?

- **Demonstrate horizontal scaling:** show that storage capacity can grow by adding nodes rather than changing the client API.
- **Improve failure tolerance:** with more placement choices, the cluster has more healthy destinations available when a node fails.
- **Demonstrate repair placement:** under-replicated chunks can be repaired onto a different eligible node when the original replica node fails.
- **Make the demo more convincing:** five independently visible storage nodes make distribution, node failure, and recovery easier to explain.

Adding nodes alone does not automatically increase each file's replica count. The current replication factor is `2`; it can remain 2 so each chunk is stored on two different nodes. Increasing the replication factor is a separate decision with additional storage and network costs.

## Scope and Assumptions

- The master and public API remain unchanged.
- Node 4 and node 5 use ports `5004` and `5005` and distinct storage directories/volumes.
- Each node sends heartbeats to the master and advertises an address reachable by the master.
- The current replication factor remains 2 unless explicitly changed in a separate task.
- Both Docker Compose and direct local startup instructions should describe the five-node configuration.

## Files to Change

### Required for Docker Compose

- `compose.yaml`
  - Add `storage-node-4` and `storage-node-5` services.
  - Configure `SERVER_PORT`, `HEARTBEAT_MASTER_URL`, `STORAGE_NODE_URL`, and `STORAGE_BASE_DIR` for each service.
  - Give each node a separate named chunk-data volume.
  - Add both service URLs to the master's `STORAGE_NODES` value.
  - Include both services in the master's startup dependencies.

### Required for Direct Local Startup

- `master-service/src/main/resources/application.properties`
  - Add `http://localhost:5004` and `http://localhost:5005` to the default `storage.nodes` list.
- `docs/setup/wsl-setup.md`
  - Add startup commands and port/storage notes for nodes 4 and 5.

### User-Facing Documentation

- `README.md`
  - Update the architecture diagram, service/port table, quick-start steps, and references to three nodes.
- `docs/api/endpoints.md`
  - Update the example cluster addresses if it is intended to describe the default five-node setup. The API endpoint contracts themselves do not change.
- `docs/planning/docker-deployment-plan.md`
  - Update the planned cluster size and acceptance checks if the five-node expansion is completed before or during Docker-plan implementation.

## Implementation Phases

1. Add nodes 4 and 5 with unique ports, advertised URLs, and isolated persistent data volumes.
2. Add both node URLs to the master's configured node list for Compose and direct local runs.
3. Update setup, architecture, and port documentation to consistently describe five nodes.
4. Start all services and confirm `node-1` through `node-5` appear as active.
5. Upload a file and verify replica placement uses two distinct nodes per chunk.
6. Simulate a node failure and confirm the master can repair under-replicated chunks onto a healthy node.
7. Restart one storage container and verify its chunk data persists in its own volume.

## Risks and Considerations

- More services use more memory and CPU, even when idle.
- Each node must have a separate data directory or volume; sharing storage paths can overwrite or expose the wrong chunks.
- Docker-internal advertised URLs must use Compose service names, not `localhost`.
- Adding nodes does not rebalance existing replicas automatically. Existing data may remain on the original three nodes until uploads or repairs place replicas elsewhere.
- A replication factor of 2 on five nodes improves placement choice, but does not mean every chunk is copied to all five nodes.

## Acceptance Criteria

- [ ] Five storage services start with unique ports and isolated persistent data.
- [ ] The master discovers all five nodes through heartbeats.
- [ ] Upload and download work with the five-node configuration.
- [ ] Each chunk has the configured replication factor of 2 across distinct nodes.
- [ ] Failure and repair work with node 4 or node 5 as well as the original nodes.
- [ ] README and setup docs consistently describe the five-node setup.

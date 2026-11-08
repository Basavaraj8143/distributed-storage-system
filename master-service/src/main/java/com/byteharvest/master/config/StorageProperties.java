package com.byteharvest.master.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Storage settings, bound from the {@code storage.*} properties and validated once at startup.
 * Services receive this object instead of owning constants, so a different replication factor
 * or chunk size needs only a configuration change (or a different object in a test).
 *
 * <pre>
 * storage.replication-factor=2          copies kept of every chunk
 * storage.chunk-size-bytes=1048576      size of one chunk (1 MiB)
 * storage.nodes=http://a:5001,http://b:5002
 * </pre>
 *
 * @param replicationFactor number of copies kept of every chunk, at least 1
 * @param chunkSizeBytes    size of one chunk in bytes, at least {@value #MIN_CHUNK_SIZE_BYTES}
 * @param nodes             base URLs of the storage nodes this master may use
 */
@ConfigurationProperties(prefix = "storage")
public record StorageProperties(
        @DefaultValue("2") int replicationFactor,
        @DefaultValue("1048576") int chunkSizeBytes,
        List<String> nodes) {

    public static final int MIN_CHUNK_SIZE_BYTES = 1024;

    public StorageProperties {
        if (replicationFactor < 1) {
            throw new IllegalArgumentException(
                    "storage.replication-factor must be at least 1, but was " + replicationFactor);
        }
        if (chunkSizeBytes < MIN_CHUNK_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    "storage.chunk-size-bytes must be at least " + MIN_CHUNK_SIZE_BYTES + ", but was " + chunkSizeBytes);
        }

        nodes = nodes == null
                ? List.of()
                : nodes.stream().map(String::trim).filter(node -> !node.isEmpty()).distinct().toList();

        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("No storage nodes configured. Set storage.nodes (or STORAGE_NODES).");
        }
        if (replicationFactor > nodes.size()) {
            throw new IllegalArgumentException(
                    "storage.replication-factor (" + replicationFactor + ") is larger than the number of configured "
                            + "storage nodes (" + nodes.size() + "). Add nodes to storage.nodes or lower the replication factor.");
        }
    }
}

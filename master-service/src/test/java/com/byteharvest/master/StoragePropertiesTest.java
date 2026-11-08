package com.byteharvest.master;

import com.byteharvest.master.config.StorageProperties;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoragePropertiesTest {

    private static final List<String> THREE_NODES = List.of("http://a:5001", "http://b:5002", "http://c:5003");

    @Test
    void acceptsAValidConfiguration() {
        StorageProperties properties = new StorageProperties(2, 1024 * 1024, THREE_NODES);

        assertEquals(2, properties.replicationFactor());
        assertEquals(1024 * 1024, properties.chunkSizeBytes());
        assertEquals(THREE_NODES, properties.nodes());
    }

    @Test
    void replicationFactorEqualToTheNodeCountIsAllowed() {
        StorageProperties properties = new StorageProperties(3, 1024, THREE_NODES);

        assertEquals(3, properties.replicationFactor());
    }

    @Test
    void rejectsAReplicationFactorBelowOne() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new StorageProperties(0, 1024, THREE_NODES));

        assertTrue(error.getMessage().contains("storage.replication-factor"));
    }

    @Test
    void rejectsAReplicationFactorLargerThanTheNumberOfNodes() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new StorageProperties(4, 1024, THREE_NODES));

        assertTrue(error.getMessage().contains("larger than the number of configured storage nodes"));
    }

    @Test
    void rejectsAChunkSizeBelowTheMinimum() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new StorageProperties(2, StorageProperties.MIN_CHUNK_SIZE_BYTES - 1, THREE_NODES));

        assertTrue(error.getMessage().contains("storage.chunk-size-bytes"));
    }

    @Test
    void rejectsMissingOrEmptyNodeLists() {
        assertThrows(IllegalArgumentException.class, () -> new StorageProperties(1, 1024, null));
        assertThrows(IllegalArgumentException.class, () -> new StorageProperties(1, 1024, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new StorageProperties(1, 1024, List.of("  ", "")));
    }

    @Test
    void trimsBlankAndDuplicateNodeEntries() {
        StorageProperties properties = new StorageProperties(2, 1024,
                Arrays.asList(" http://a:5001 ", "", "http://b:5002", "http://a:5001"));

        assertEquals(List.of("http://a:5001", "http://b:5002"), properties.nodes());
    }

    @Test
    void duplicateNodesDoNotCountTowardsTheReplicationFactor() {
        // 3 entries but only 2 distinct nodes, so a factor of 3 cannot be met.
        assertThrows(IllegalArgumentException.class,
                () -> new StorageProperties(3, 1024, List.of("http://a:5001", "http://b:5002", "http://a:5001")));
    }
}

package com.byteharvest.master;

import com.byteharvest.master.config.StorageProperties;
import com.byteharvest.master.entity.ChunkMetadataEntity;
import com.byteharvest.master.entity.ChunkReplicaEntity;
import com.byteharvest.master.model.ReconcileResult;
import com.byteharvest.master.repository.ChunkMetadataRepository;
import com.byteharvest.master.repository.ChunkReplicaRepository;
import com.byteharvest.master.repository.FileMetadataRepository;
import com.byteharvest.master.service.ChunkService;
import com.byteharvest.master.service.EventLogService;
import com.byteharvest.master.service.HeartbeatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What happens when a node that was FAILED (or is new to this master) reports the chunks it still holds.
 */
class NodeReconcileTest {

    private static final String RETURNING = "http://localhost:5003";
    private static final String NODE_A = "http://localhost:5001";
    private static final String NODE_B = "http://localhost:5002";

    private static final byte[] CONTENT = "the real chunk content".getBytes(StandardCharsets.UTF_8);
    private static final byte[] CORRUPT = "not the real content".getBytes(StandardCharsets.UTF_8);

    private ChunkService chunkService;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private EventLogService eventLogService;

    @Mock
    private HeartbeatService heartbeatService;

    @Mock
    private FileMetadataRepository fileMetadataRepository;

    @Mock
    private ChunkMetadataRepository chunkMetadataRepository;

    @Mock
    private ChunkReplicaRepository chunkReplicaRepository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        chunkService = new ChunkService(
                new StorageProperties(2, 1024 * 1024, List.of(NODE_A, NODE_B, RETURNING)),
                restTemplate, eventLogService, heartbeatService,
                fileMetadataRepository, chunkMetadataRepository);
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private ChunkMetadataEntity knownChunk(String chunkId, String... replicaUrls) {
        ChunkMetadataEntity chunk = new ChunkMetadataEntity();
        chunk.setChunkId(chunkId);
        chunk.setChunkIndex(0);
        chunk.setChecksum(sha256(CONTENT));
        for (String url : replicaUrls) {
            ChunkReplicaEntity replica = new ChunkReplicaEntity();
            replica.setNodeUrl(url);
            replica.setChunkMetadata(chunk);
            chunk.getReplicas().add(replica);
        }
        when(chunkMetadataRepository.findByChunkId(chunkId)).thenReturn(chunk);
        return chunk;
    }

    private void nodeReports(String... chunkIds) {
        when(restTemplate.getForObject(RETURNING + "/chunks", String[].class)).thenReturn(chunkIds);
    }

    private void nodeHolds(String chunkId, byte[] data) {
        when(restTemplate.getForObject(RETURNING + "/getChunk/" + chunkId, byte[].class)).thenReturn(data);
    }

    private static List<String> urlsOf(ChunkMetadataEntity chunk) {
        return chunk.getReplicas().stream().map(ChunkReplicaEntity::getNodeUrl).collect(Collectors.toList());
    }

    @Test
    void validCopyIsAdoptedWhenTheChunkIsUnderReplicated() {
        ChunkMetadataEntity chunk = knownChunk("c1", NODE_A);
        nodeReports("c1");
        nodeHolds("c1", CONTENT);

        ReconcileResult result = chunkService.reconcileNode(RETURNING, Set.of(NODE_A, RETURNING));

        assertEquals(1, result.adopted());
        assertTrue(urlsOf(chunk).contains(RETURNING), "the replica record is written back into the metadata");
        verify(chunkMetadataRepository).save(chunk);
        verify(restTemplate, never()).delete(anyString());
    }

    @Test
    void copyIsAdoptedWhenTheOtherReplicaIsOnlyStale() {
        // The chunk has 2 records, but node B is STALE (not active), so only 1 copy is really healthy.
        ChunkMetadataEntity chunk = knownChunk("c1", NODE_A, NODE_B);
        nodeReports("c1");
        nodeHolds("c1", CONTENT);

        ReconcileResult result = chunkService.reconcileNode(RETURNING, Set.of(NODE_A, RETURNING));

        assertEquals(1, result.adopted());
        assertTrue(urlsOf(chunk).contains(RETURNING));
    }

    @Test
    void surplusCopyIsDeletedWhenEnoughHealthyReplicasExist() {
        // Repair already re-replicated this chunk while the node was away.
        ChunkMetadataEntity chunk = knownChunk("c1", NODE_A, NODE_B);
        nodeReports("c1");

        ReconcileResult result = chunkService.reconcileNode(RETURNING, Set.of(NODE_A, NODE_B, RETURNING));

        assertEquals(1, result.surplusRemoved());
        assertEquals(0, result.adopted());
        verify(restTemplate).delete(RETURNING + "/deleteChunk/c1");
        verify(chunkMetadataRepository, never()).save(chunk);
        verify(restTemplate, never()).getForObject(RETURNING + "/getChunk/c1", byte[].class);
    }

    @Test
    void corruptCopyIsDeletedNotAdopted() {
        ChunkMetadataEntity chunk = knownChunk("c1", NODE_A);
        nodeReports("c1");
        nodeHolds("c1", CORRUPT);

        ReconcileResult result = chunkService.reconcileNode(RETURNING, Set.of(NODE_A, RETURNING));

        assertEquals(1, result.corruptRemoved());
        assertEquals(0, result.adopted());
        verify(restTemplate).delete(RETURNING + "/deleteChunk/c1");
        verify(chunkMetadataRepository, never()).save(chunk);
    }

    @Test
    void chunkAlreadyRecordedForTheNodeIsLeftAlone() {
        ChunkMetadataEntity chunk = knownChunk("c1", NODE_A, RETURNING);
        nodeReports("c1");

        ReconcileResult result = chunkService.reconcileNode(RETURNING, Set.of(NODE_A, RETURNING));

        assertEquals(ReconcileResult.EMPTY, result);
        verify(chunkMetadataRepository, never()).save(chunk);
        verify(restTemplate, never()).delete(anyString());
    }

    @Test
    void unknownChunksAreNeverDeleted() {
        // No metadata for "mystery": a file deleted while the node was away, or an upload still in progress.
        nodeReports("mystery");

        ReconcileResult result = chunkService.reconcileNode(RETURNING, Set.of(NODE_A, RETURNING));

        assertEquals(1, result.unknownKept());
        verify(restTemplate, never()).delete(anyString());
        verify(chunkMetadataRepository, never()).save(any());
    }

    @Test
    void oneUnreadableChunkDoesNotStopTheRest() {
        knownChunk("c1", NODE_A);
        ChunkMetadataEntity second = knownChunk("c2", NODE_A);
        nodeReports("c1", "c2");
        when(restTemplate.getForObject(RETURNING + "/getChunk/c1", byte[].class)).thenThrow(new RuntimeException("boom"));
        nodeHolds("c2", CONTENT);

        ReconcileResult result = chunkService.reconcileNode(RETURNING, Set.of(NODE_A, RETURNING));

        assertEquals(1, result.skipped());
        assertEquals(1, result.adopted());
        assertTrue(urlsOf(second).contains(RETURNING));
    }

    @Test
    void failureToListChunksIsReportedSoTheCallerCanRetry() {
        when(restTemplate.getForObject(RETURNING + "/chunks", String[].class)).thenThrow(new RuntimeException("node down"));

        assertThrows(RuntimeException.class, () -> chunkService.reconcileNode(RETURNING, Set.of(RETURNING)));
    }
}

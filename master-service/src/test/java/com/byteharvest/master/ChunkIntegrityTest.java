package com.byteharvest.master;

import com.byteharvest.master.model.ChunkMetadata;
import com.byteharvest.master.service.ChunkService;
import com.byteharvest.master.service.EventLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChunkIntegrityTest {

    @InjectMocks
    private ChunkService chunkService;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private EventLogService eventLogService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Configure local node list in chunk service
        ReflectionTestUtils.setField(chunkService, "configuredNodes", "http://localhost:5001,http://localhost:5002,http://localhost:5003");
        chunkService.initNodes();
    }

    @Test
    void testUploadGeneratesChecksum() {
        byte[] fileBytes = "Hello World Distributed Storage".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", fileBytes);

        String fileId = chunkService.handleUpload(file);
        assertNotNull(fileId);

        // Retrieve chunks metadata from storage
        @SuppressWarnings("unchecked")
        Map<String, List<ChunkMetadata>> storage = (Map<String, List<ChunkMetadata>>) ReflectionTestUtils.getField(chunkService, "storage");
        assertNotNull(storage);
        List<ChunkMetadata> metadata = storage.get(fileId);
        assertNotNull(metadata);
        assertFalse(metadata.isEmpty());

        for (ChunkMetadata chunk : metadata) {
            assertNotNull(chunk.getChecksum());
            assertEquals(64, chunk.getChecksum().length()); // SHA-256 is 64 hex characters
        }
    }

    @Test
    void testDownloadVerifiesIntegrityAndRepairsCorruption() {
        byte[] content = "Some test chunk content to verify".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", content);

        // Mock upload to capture metadata
        String fileId = chunkService.handleUpload(file);
        clearInvocations(restTemplate);

        @SuppressWarnings("unchecked")
        Map<String, List<ChunkMetadata>> storage = (Map<String, List<ChunkMetadata>>) ReflectionTestUtils.getField(chunkService, "storage");
        ChunkMetadata chunk = storage.get(fileId).get(0);
        String correctChecksum = chunk.getChecksum();

        // Ensure we have two replica URLs
        List<String> replicaUrls = chunk.getNodeUrls();
        assertEquals(2, replicaUrls.size());
        String node1 = replicaUrls.get(0);
        String node2 = replicaUrls.get(1);

        // Scenario: node1 returns corrupt content, node2 returns correct content
        byte[] corruptContent = "Corrupt test chunk content to verify".getBytes();
        
        // Mock getChunk calls
        when(restTemplate.getForObject(node1 + "/getChunk/" + chunk.getChunkId(), byte[].class))
                .thenReturn(corruptContent);
        when(restTemplate.getForObject(node2 + "/getChunk/" + chunk.getChunkId(), byte[].class))
                .thenReturn(content);

        // Call download
        byte[] downloadedBytes = chunkService.download(fileId);

        // Assert content is correct (corrupt node bypassed)
        assertArrayEquals(content, downloadedBytes);

        // Verify it tried to repair the corrupted node1 by overwriting
        verify(restTemplate, times(1)).postForEntity(
                eq(node1 + "/storeChunk"),
                any(),
                eq(String.class)
        );

        // verify event log was warned about corruption and notified about repair
        verify(eventLogService, times(1)).warn(eq("INTEGRITY"), contains("Corrupted chunk detected"));
        verify(eventLogService, times(1)).info(eq("INTEGRITY"), contains("Repaired corrupted chunk"));
    }

    @Test
    void testDownloadRemovesReplicaOnRepairFailure() {
        byte[] content = "Target test data for overwrite fail".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", content);

        String fileId = chunkService.handleUpload(file);
        clearInvocations(restTemplate);

        @SuppressWarnings("unchecked")
        Map<String, List<ChunkMetadata>> storage = (Map<String, List<ChunkMetadata>>) ReflectionTestUtils.getField(chunkService, "storage");
        ChunkMetadata chunk = storage.get(fileId).get(0);

        List<String> replicaUrls = chunk.getNodeUrls();
        String node1 = replicaUrls.get(0);
        String node2 = replicaUrls.get(1);

        byte[] corruptContent = "Bad content".getBytes();
        
        when(restTemplate.getForObject(node1 + "/getChunk/" + chunk.getChunkId(), byte[].class))
                .thenReturn(corruptContent);
        when(restTemplate.getForObject(node2 + "/getChunk/" + chunk.getChunkId(), byte[].class))
                .thenReturn(content);

        // Mock storeChunk on node1 to throw exception (write failure)
        when(restTemplate.postForEntity(eq(node1 + "/storeChunk"), any(), eq(String.class)))
                .thenThrow(new RuntimeException("Node offline for write"));

        byte[] downloadedBytes = chunkService.download(fileId);
        assertArrayEquals(content, downloadedBytes);

        // verify node1 got removed from chunk node list
        assertFalse(chunk.getNodeUrls().contains(node1));
        assertTrue(chunk.getNodeUrls().contains(node2));
        assertEquals(1, chunk.getNodeUrls().size());
    }

    @Test
    void testScheduledIntegrityVerificationAndRepair() {
        byte[] content = "Data to check periodically".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", content);

        String fileId = chunkService.handleUpload(file);
        clearInvocations(restTemplate);

        @SuppressWarnings("unchecked")
        Map<String, List<ChunkMetadata>> storage = (Map<String, List<ChunkMetadata>>) ReflectionTestUtils.getField(chunkService, "storage");
        ChunkMetadata chunk = storage.get(fileId).get(0);

        List<String> replicaUrls = chunk.getNodeUrls();
        String node1 = replicaUrls.get(0);
        String node2 = replicaUrls.get(1);

        // Setup active node list
        Set<String> activeNodes = new HashSet<>(replicaUrls);

        byte[] corruptContent = "Bad content".getBytes();

        // node1 is corrupt, node2 is valid
        when(restTemplate.getForObject(node1 + "/getChunk/" + chunk.getChunkId(), byte[].class))
                .thenReturn(corruptContent);
        when(restTemplate.getForObject(node2 + "/getChunk/" + chunk.getChunkId(), byte[].class))
                .thenReturn(content);

        // Execute periodic verify and repair
        int repairedCount = chunkService.verifyAndRepairCorruptions(activeNodes);

        // Expect 1 repair count
        assertEquals(1, repairedCount);

        // Verify overwrite attempt was made to node1
        verify(restTemplate, times(1)).postForEntity(eq(node1 + "/storeChunk"), any(), eq(String.class));
    }
}

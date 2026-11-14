package com.byteharvest.master;

import com.byteharvest.master.client.RestStorageNodeClient;
import com.byteharvest.master.client.StorageNodeClient;
import com.byteharvest.master.config.StorageProperties;
import com.byteharvest.master.entity.ChunkMetadataEntity;
import com.byteharvest.master.entity.ChunkReplicaEntity;
import com.byteharvest.master.entity.FileMetadataEntity;
import com.byteharvest.master.exception.InsufficientNodesException;
import com.byteharvest.master.placement.RandomPlacementStrategy;
import com.byteharvest.master.repository.ChunkMetadataRepository;
import com.byteharvest.master.repository.FileMetadataRepository;
import com.byteharvest.master.service.ChunkRepairService;
import com.byteharvest.master.service.DownloadService;
import com.byteharvest.master.service.EventLogService;
import com.byteharvest.master.service.HeartbeatService;
import com.byteharvest.master.service.UploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChunkIntegrityTest {

    private UploadService uploadService;
    private DownloadService downloadService;
    private ChunkRepairService repairService;

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

    private Map<String, FileMetadataEntity> fakeDatabase = new HashMap<>();
    private List<ChunkMetadataEntity> fakeChunkDatabase = new ArrayList<>();

    private static final List<String> FIVE_NODES = List.of(
            "http://localhost:5001", "http://localhost:5002", "http://localhost:5003",
            "http://localhost:5004", "http://localhost:5005");

    private StorageNodeClient nodeClient() {
        return new RestStorageNodeClient(restTemplate);
    }

    private UploadService uploadServiceWith(int replicationFactor, int chunkSizeBytes) {
        return new UploadService(
                new StorageProperties(replicationFactor, chunkSizeBytes, FIVE_NODES),
                nodeClient(), new RandomPlacementStrategy(), eventLogService, heartbeatService,
                fileMetadataRepository, chunkMetadataRepository);
    }

    private ChunkRepairService repairServiceWith(int replicationFactor) {
        return new ChunkRepairService(
                new StorageProperties(replicationFactor, 1024 * 1024, FIVE_NODES),
                nodeClient(), new RandomPlacementStrategy(), eventLogService, chunkMetadataRepository);
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        uploadService = uploadServiceWith(2, 1024 * 1024);
        downloadService = new DownloadService(nodeClient(), eventLogService, fileMetadataRepository, chunkMetadataRepository);
        repairService = repairServiceWith(2);

        when(heartbeatService.getActiveNodeUrls()).thenReturn(Set.of(
                "http://localhost:5001", "http://localhost:5002", "http://localhost:5003",
                "http://localhost:5004", "http://localhost:5005"));

        fakeDatabase.clear();
        fakeChunkDatabase.clear();

        when(fileMetadataRepository.save(any(FileMetadataEntity.class))).thenAnswer(invocation -> {
            FileMetadataEntity entity = invocation.getArgument(0);
            fakeDatabase.put(entity.getFileId(), entity);
            return entity;
        });

        when(chunkMetadataRepository.save(any(ChunkMetadataEntity.class))).thenAnswer(invocation -> {
            ChunkMetadataEntity entity = invocation.getArgument(0);
            if (!fakeChunkDatabase.contains(entity)) {
                fakeChunkDatabase.add(entity);
            }
            return entity;
        });

        when(fileMetadataRepository.findById(anyString())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            FileMetadataEntity fileEntity = fakeDatabase.get(id);
            if (fileEntity != null) {
                List<ChunkMetadataEntity> chunks = fakeChunkDatabase.stream()
                        .filter(c -> c.getFileMetadata().getFileId().equals(id))
                        .collect(Collectors.toList());
                fileEntity.setChunks(chunks);
            }
            return Optional.ofNullable(fileEntity);
        });

        when(chunkMetadataRepository.findAll()).thenAnswer(invocation -> new ArrayList<>(fakeChunkDatabase));
    }

    @Test
    void testUploadGeneratesChecksum() {
        byte[] fileBytes = "Hello World Distributed Storage".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", fileBytes);

        String fileId = uploadService.handleUpload(file);
        assertNotNull(fileId);

        FileMetadataEntity metadata = fakeDatabase.get(fileId);
        assertNotNull(metadata);

        List<ChunkMetadataEntity> chunks = fakeChunkDatabase.stream()
                .filter(c -> c.getFileMetadata().getFileId().equals(fileId))
                .collect(Collectors.toList());
        assertFalse(chunks.isEmpty());

        for (ChunkMetadataEntity chunk : chunks) {
            assertNotNull(chunk.getChecksum());
            assertEquals(64, chunk.getChecksum().length());
        }
    }

    @Test
    void uploadOnlyUsesNodesThatAreActive() {
        when(heartbeatService.getActiveNodeUrls()).thenReturn(Set.of("http://localhost:5003", "http://localhost:5004"));
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "hello".getBytes());

        String fileId = uploadService.handleUpload(file);

        ChunkMetadataEntity chunk = fakeChunkDatabase.stream()
                .filter(c -> c.getFileMetadata().getFileId().equals(fileId))
                .findFirst().orElseThrow();
        Set<String> used = chunk.getReplicas().stream().map(ChunkReplicaEntity::getNodeUrl).collect(Collectors.toSet());

        assertEquals(Set.of("http://localhost:5003", "http://localhost:5004"), used);
        verify(restTemplate, never()).postForEntity(eq("http://localhost:5001/storeChunk"), any(), eq(String.class));
    }

    @Test
    void uploadTriesAnotherNodeWhenASendFails() {
        when(restTemplate.postForEntity(eq("http://localhost:5001/storeChunk"), any(), eq(String.class)))
                .thenThrow(new RuntimeException("Connection refused"));
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "hello".getBytes());

        String fileId = uploadService.handleUpload(file);

        ChunkMetadataEntity chunk = fakeChunkDatabase.stream()
                .filter(c -> c.getFileMetadata().getFileId().equals(fileId))
                .findFirst().orElseThrow();
        List<String> used = chunk.getReplicas().stream().map(ChunkReplicaEntity::getNodeUrl).collect(Collectors.toList());

        assertEquals(2, used.size());
        assertFalse(used.contains("http://localhost:5001"));
    }

    @Test
    void uploadFailsClearlyWhenTooFewNodesAreActive() {
        when(heartbeatService.getActiveNodeUrls()).thenReturn(Set.of("http://localhost:5003"));
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "hello".getBytes());

        assertThrows(InsufficientNodesException.class, () -> uploadService.handleUpload(file));
    }

    @Test
    void uploadHonoursTheConfiguredReplicationFactor() {
        uploadService = uploadServiceWith(3, 1024 * 1024);
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "hello".getBytes());

        String fileId = uploadService.handleUpload(file);

        ChunkMetadataEntity chunk = fakeChunkDatabase.stream()
                .filter(c -> c.getFileMetadata().getFileId().equals(fileId))
                .findFirst().orElseThrow();
        assertEquals(3, chunk.getReplicas().size());
    }

    @Test
    void uploadFailsWhenFewerNodesAreActiveThanTheConfiguredReplicationFactor() {
        uploadService = uploadServiceWith(3, 1024 * 1024);
        when(heartbeatService.getActiveNodeUrls()).thenReturn(Set.of("http://localhost:5001", "http://localhost:5002"));
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "hello".getBytes());

        assertThrows(InsufficientNodesException.class, () -> uploadService.handleUpload(file));
    }

    @Test
    void uploadHonoursTheConfiguredChunkSize() {
        uploadService = uploadServiceWith(2, 1024);
        MockMultipartFile file = new MockMultipartFile("file", "test.bin", "application/octet-stream", new byte[2500]);

        String fileId = uploadService.handleUpload(file);

        long chunkCount = fakeChunkDatabase.stream()
                .filter(c -> c.getFileMetadata().getFileId().equals(fileId))
                .count();
        assertEquals(3, chunkCount, "2500 bytes in 1024-byte chunks is 1024 + 1024 + 452");
    }

    @Test
    void raisingTheReplicationFactorMakesRepairAddTheMissingCopy() {
        byte[] content = "chunk that gains a third copy".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", content);
        String fileId = uploadService.handleUpload(file);

        ChunkMetadataEntity chunk = fakeChunkDatabase.stream()
                .filter(c -> c.getFileMetadata().getFileId().equals(fileId))
                .findFirst().orElseThrow();
        assertEquals(2, chunk.getReplicas().size());

        repairService = repairServiceWith(3);
        when(restTemplate.getForObject(anyString(), eq(byte[].class))).thenReturn(content);

        int added = repairService.repairUnderReplicatedChunks(new HashSet<>(FIVE_NODES));

        assertEquals(1, added);
        assertEquals(3, chunk.getReplicas().size());
    }

    @Test
    void testDownloadVerifiesIntegrityAndRepairsCorruption() {
        byte[] content = "Some test chunk content to verify".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", content);

        String fileId = uploadService.handleUpload(file);
        clearInvocations(restTemplate);

        ChunkMetadataEntity chunk = fakeChunkDatabase.stream()
                .filter(c -> c.getFileMetadata().getFileId().equals(fileId))
                .findFirst().orElseThrow();

        List<ChunkReplicaEntity> replicas = chunk.getReplicas();
        assertEquals(2, replicas.size());
        String node1 = replicas.get(0).getNodeUrl();
        String node2 = replicas.get(1).getNodeUrl();

        byte[] corruptContent = "Corrupt test chunk content to verify".getBytes();

        when(restTemplate.getForObject(node1 + "/getChunk/" + chunk.getChunkId(), byte[].class))
                .thenReturn(corruptContent);
        when(restTemplate.getForObject(node2 + "/getChunk/" + chunk.getChunkId(), byte[].class))
                .thenReturn(content);

        byte[] downloadedBytes = downloadService.download(fileId);

        assertArrayEquals(content, downloadedBytes);

        verify(restTemplate, times(1)).postForEntity(
                eq(node1 + "/storeChunk"),
                any(),
                eq(String.class)
        );

        verify(eventLogService, times(1)).warn(eq("INTEGRITY"), contains("Corrupted chunk detected"));
        verify(eventLogService, times(1)).info(eq("INTEGRITY"), contains("Repaired corrupted chunk"));
    }

    @Test
    void testDownloadRemovesReplicaOnRepairFailure() {
        byte[] content = "Target test data for overwrite fail".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", content);

        String fileId = uploadService.handleUpload(file);
        clearInvocations(restTemplate);

        ChunkMetadataEntity chunk = fakeChunkDatabase.stream()
                .filter(c -> c.getFileMetadata().getFileId().equals(fileId))
                .findFirst().orElseThrow();

        List<ChunkReplicaEntity> replicas = chunk.getReplicas();
        String node1 = replicas.get(0).getNodeUrl();
        String node2 = replicas.get(1).getNodeUrl();

        byte[] corruptContent = "Bad content".getBytes();

        when(restTemplate.getForObject(node1 + "/getChunk/" + chunk.getChunkId(), byte[].class))
                .thenReturn(corruptContent);
        when(restTemplate.getForObject(node2 + "/getChunk/" + chunk.getChunkId(), byte[].class))
                .thenReturn(content);

        when(restTemplate.postForEntity(eq(node1 + "/storeChunk"), any(), eq(String.class)))
                .thenThrow(new RuntimeException("Node offline for write"));

        byte[] downloadedBytes = downloadService.download(fileId);
        assertArrayEquals(content, downloadedBytes);

        List<String> remainingNodes = chunk.getReplicas().stream().map(ChunkReplicaEntity::getNodeUrl).collect(Collectors.toList());
        assertFalse(remainingNodes.contains(node1));
        assertTrue(remainingNodes.contains(node2));
        assertEquals(1, remainingNodes.size());
    }

    @Test
    void testScheduledIntegrityVerificationAndRepair() {
        byte[] content = "Data to check periodically".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", content);

        String fileId = uploadService.handleUpload(file);
        clearInvocations(restTemplate);

        ChunkMetadataEntity chunk = fakeChunkDatabase.stream()
                .filter(c -> c.getFileMetadata().getFileId().equals(fileId))
                .findFirst().orElseThrow();

        List<String> replicaUrls = chunk.getReplicas().stream().map(ChunkReplicaEntity::getNodeUrl).collect(Collectors.toList());
        String node1 = replicaUrls.get(0);
        String node2 = replicaUrls.get(1);

        Set<String> activeNodes = new HashSet<>(replicaUrls);

        byte[] corruptContent = "Bad content".getBytes();

        when(restTemplate.getForObject(node1 + "/getChunk/" + chunk.getChunkId(), byte[].class))
                .thenReturn(corruptContent);
        when(restTemplate.getForObject(node2 + "/getChunk/" + chunk.getChunkId(), byte[].class))
                .thenReturn(content);

        int repairedCount = repairService.verifyAndRepairCorruptions(activeNodes);

        assertEquals(1, repairedCount);

        verify(restTemplate, times(1)).postForEntity(eq(node1 + "/storeChunk"), any(), eq(String.class));
    }
}

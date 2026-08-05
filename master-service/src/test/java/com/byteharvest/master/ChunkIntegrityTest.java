package com.byteharvest.master;

import com.byteharvest.master.entity.ChunkMetadataEntity;
import com.byteharvest.master.entity.ChunkReplicaEntity;
import com.byteharvest.master.entity.FileMetadataEntity;
import com.byteharvest.master.repository.ChunkMetadataRepository;
import com.byteharvest.master.repository.ChunkReplicaRepository;
import com.byteharvest.master.repository.FileMetadataRepository;
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
import java.util.stream.Collectors;

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

    @Mock
    private FileMetadataRepository fileMetadataRepository;

    @Mock
    private ChunkMetadataRepository chunkMetadataRepository;

    @Mock
    private ChunkReplicaRepository chunkReplicaRepository;

    private Map<String, FileMetadataEntity> fakeDatabase = new HashMap<>();
    private List<ChunkMetadataEntity> fakeChunkDatabase = new ArrayList<>();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(chunkService, "configuredNodes", "http://localhost:5001,http://localhost:5002,http://localhost:5003,http://localhost:5004,http://localhost:5005");
        chunkService.initNodes();

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

        String fileId = chunkService.handleUpload(file);
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
    void testDownloadVerifiesIntegrityAndRepairsCorruption() {
        byte[] content = "Some test chunk content to verify".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", content);

        String fileId = chunkService.handleUpload(file);
        clearInvocations(restTemplate);

        ChunkMetadataEntity chunk = fakeChunkDatabase.stream()
                .filter(c -> c.getFileMetadata().getFileId().equals(fileId))
                .findFirst().orElseThrow();
        String correctChecksum = chunk.getChecksum();

        List<ChunkReplicaEntity> replicas = chunk.getReplicas();
        assertEquals(2, replicas.size());
        String node1 = replicas.get(0).getNodeUrl();
        String node2 = replicas.get(1).getNodeUrl();

        byte[] corruptContent = "Corrupt test chunk content to verify".getBytes();
        
        when(restTemplate.getForObject(node1 + "/getChunk/" + chunk.getChunkId(), byte[].class))
                .thenReturn(corruptContent);
        when(restTemplate.getForObject(node2 + "/getChunk/" + chunk.getChunkId(), byte[].class))
                .thenReturn(content);

        byte[] downloadedBytes = chunkService.download(fileId);

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

        String fileId = chunkService.handleUpload(file);
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

        byte[] downloadedBytes = chunkService.download(fileId);
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

        String fileId = chunkService.handleUpload(file);
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

        int repairedCount = chunkService.verifyAndRepairCorruptions(activeNodes);

        assertEquals(1, repairedCount);

        verify(restTemplate, times(1)).postForEntity(eq(node1 + "/storeChunk"), any(), eq(String.class));
    }
}

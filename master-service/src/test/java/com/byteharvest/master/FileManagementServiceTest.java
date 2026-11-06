package com.byteharvest.master;

import com.byteharvest.master.entity.ChunkMetadataEntity;
import com.byteharvest.master.entity.ChunkReplicaEntity;
import com.byteharvest.master.entity.FileMetadataEntity;
import com.byteharvest.master.exception.ChunkUnavailableException;
import com.byteharvest.master.exception.StoredFileNotFoundException;
import com.byteharvest.master.model.FileDetails;
import com.byteharvest.master.model.FileSummary;
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
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileManagementServiceTest {

    private static final String FILE_ID = "123e4567-e89b-12d3-a456-426614174000";

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

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    /** Builds a file whose chunk i has replicasPerChunk[i] replicas, on http://localhost:5001, 5002, ... */
    private FileMetadataEntity fileWithChunks(String name, int... replicasPerChunk) {
        FileMetadataEntity file = new FileMetadataEntity();
        file.setFileId(FILE_ID);
        file.setOriginalFilename(name);

        for (int i = 0; i < replicasPerChunk.length; i++) {
            ChunkMetadataEntity chunk = new ChunkMetadataEntity();
            chunk.setChunkId("chunk-" + i);
            chunk.setChunkIndex(i);
            chunk.setChecksum("checksum-" + i);
            chunk.setFileMetadata(file);

            for (int r = 0; r < replicasPerChunk[i]; r++) {
                ChunkReplicaEntity replica = new ChunkReplicaEntity();
                replica.setNodeUrl("http://localhost:" + (5001 + r));
                replica.setChunkMetadata(chunk);
                chunk.getReplicas().add(replica);
            }
            file.getChunks().add(chunk);
        }
        return file;
    }

    // ---- delete ----

    @Test
    void deleteFileRemovesMetadataAndChunksFromEveryReplicaNode() {
        FileMetadataEntity file = fileWithChunks("a.txt", 2, 2);
        when(fileMetadataRepository.findById(FILE_ID)).thenReturn(Optional.of(file));

        chunkService.deleteFile(FILE_ID);

        verify(fileMetadataRepository).delete(file);
        verify(restTemplate).delete("http://localhost:5001/deleteChunk/chunk-0");
        verify(restTemplate).delete("http://localhost:5002/deleteChunk/chunk-0");
        verify(restTemplate).delete("http://localhost:5001/deleteChunk/chunk-1");
        verify(restTemplate).delete("http://localhost:5002/deleteChunk/chunk-1");
        verify(eventLogService).info(eq("DELETE"), contains("Deleted fileId=" + FILE_ID));
    }

    @Test
    void deleteFileStillSucceedsWhenANodeCannotBeReached() {
        FileMetadataEntity file = fileWithChunks("a.txt", 2);
        when(fileMetadataRepository.findById(FILE_ID)).thenReturn(Optional.of(file));
        doThrow(new RuntimeException("node down")).when(restTemplate).delete("http://localhost:5002/deleteChunk/chunk-0");

        chunkService.deleteFile(FILE_ID);

        verify(fileMetadataRepository).delete(file);
        verify(restTemplate).delete("http://localhost:5001/deleteChunk/chunk-0");
        verify(eventLogService).warn(eq("DELETE"), contains("could not be removed"));
    }

    @Test
    void deleteUnknownFileThrowsNotFoundAndTouchesNothing() {
        when(fileMetadataRepository.findById(FILE_ID)).thenReturn(Optional.empty());

        assertThrows(StoredFileNotFoundException.class, () -> chunkService.deleteFile(FILE_ID));

        verify(fileMetadataRepository, never()).delete(org.mockito.ArgumentMatchers.any(FileMetadataEntity.class));
        verify(restTemplate, never()).delete(anyString());
    }

    // ---- list / details ----

    @Test
    void listFilesReportsLowestReplicaCountAcrossChunks() {
        when(fileMetadataRepository.findAll()).thenReturn(List.of(fileWithChunks("a.txt", 2, 1)));

        List<FileSummary> files = chunkService.listFiles();

        assertEquals(1, files.size());
        assertEquals("a.txt", files.get(0).filename());
        assertEquals(2, files.get(0).chunkCount());
        assertEquals(1, files.get(0).minReplicas());
        assertEquals(2, files.get(0).targetReplicas());
    }

    @Test
    void getFileDetailsReturnsChunksInIndexOrderWithReplicaUrls() {
        when(fileMetadataRepository.findById(FILE_ID)).thenReturn(Optional.of(fileWithChunks("a.txt", 2, 2)));

        FileDetails details = chunkService.getFileDetails(FILE_ID);

        assertEquals(2, details.chunkCount());
        assertEquals("chunk-0", details.chunks().get(0).chunkId());
        assertEquals("chunk-1", details.chunks().get(1).chunkId());
        assertEquals(List.of("http://localhost:5001", "http://localhost:5002"), details.chunks().get(0).nodeUrls());
    }

    @Test
    void getFileDetailsForUnknownFileThrowsNotFound() {
        when(fileMetadataRepository.findById(FILE_ID)).thenReturn(Optional.empty());

        assertThrows(StoredFileNotFoundException.class, () -> chunkService.getFileDetails(FILE_ID));
    }

    // ---- download errors are no longer wrapped in a generic RuntimeException ----

    @Test
    void downloadUnknownFileThrowsNotFound() {
        when(fileMetadataRepository.findById(FILE_ID)).thenReturn(Optional.empty());

        assertThrows(StoredFileNotFoundException.class, () -> chunkService.download(FILE_ID));
    }

    @Test
    void downloadWithAllReplicasDownThrowsChunkUnavailable() {
        when(fileMetadataRepository.findById(FILE_ID)).thenReturn(Optional.of(fileWithChunks("a.txt", 2)));
        when(restTemplate.getForObject(anyString(), eq(byte[].class))).thenThrow(new RuntimeException("node down"));

        assertThrows(ChunkUnavailableException.class, () -> chunkService.download(FILE_ID));
    }
}

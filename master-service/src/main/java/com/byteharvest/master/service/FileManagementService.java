package com.byteharvest.master.service;

import com.byteharvest.master.client.StorageNodeClient;
import com.byteharvest.master.config.StorageProperties;
import com.byteharvest.master.entity.ChunkMetadataEntity;
import com.byteharvest.master.entity.FileMetadataEntity;
import com.byteharvest.master.exception.StoredFileNotFoundException;
import com.byteharvest.master.model.ChunkInfo;
import com.byteharvest.master.model.FileDetails;
import com.byteharvest.master.model.FileSummary;
import com.byteharvest.master.repository.FileMetadataRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Looking at stored files (list, details, name) and deleting them. */
@Service
public class FileManagementService {

    private final StorageProperties properties;
    private final StorageNodeClient nodeClient;
    private final EventLogService eventLogService;
    private final FileMetadataRepository fileMetadataRepository;

    public FileManagementService(StorageProperties properties,
                                 StorageNodeClient nodeClient,
                                 EventLogService eventLogService,
                                 FileMetadataRepository fileMetadataRepository) {
        this.properties = properties;
        this.nodeClient = nodeClient;
        this.eventLogService = eventLogService;
        this.fileMetadataRepository = fileMetadataRepository;
    }

    @Transactional(readOnly = true)
    public String getOriginalFileName(String fileId) {
        Optional<FileMetadataEntity> opt = fileMetadataRepository.findById(fileId);
        return opt.map(FileMetadataEntity::getOriginalFilename).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<FileSummary> listFiles() {
        return fileMetadataRepository.findAll().stream()
                .map(file -> new FileSummary(
                        file.getFileId(),
                        file.getOriginalFilename(),
                        file.getChunks().size(),
                        minReplicaCount(file),
                        properties.replicationFactor()))
                .sorted(Comparator.comparing(FileSummary::filename,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public FileDetails getFileDetails(String fileId) {
        FileMetadataEntity file = fileMetadataRepository.findById(fileId)
                .orElseThrow(() -> new StoredFileNotFoundException(fileId));

        List<ChunkInfo> chunks = file.getChunks().stream()
                .sorted(Comparator.comparingInt(ChunkMetadataEntity::getChunkIndex))
                .map(chunk -> new ChunkInfo(
                        chunk.getChunkId(),
                        chunk.getChunkIndex(),
                        chunk.getChecksum(),
                        chunk.replicaUrls()))
                .collect(Collectors.toList());

        return new FileDetails(file.getFileId(), file.getOriginalFilename(), chunks.size(), properties.replicationFactor(), chunks);
    }

    /**
     * Deletes the file's metadata, then removes its chunks from the storage nodes.
     * Node cleanup runs only after the metadata deletion has committed, so a failed commit
     * can never leave metadata pointing at chunks that were already removed.
     * Cleanup is best effort: a node that is down keeps an orphaned chunk file.
     */
    @Transactional
    public void deleteFile(String fileId) {
        FileMetadataEntity file = fileMetadataRepository.findById(fileId)
                .orElseThrow(() -> new StoredFileNotFoundException(fileId));

        Map<String, List<String>> chunkNodes = new LinkedHashMap<>();
        for (ChunkMetadataEntity chunk : file.getChunks()) {
            chunkNodes.put(chunk.getChunkId(), chunk.replicaUrls());
        }

        fileMetadataRepository.delete(file);

        Runnable cleanup = () -> removeChunksFromNodes(fileId, chunkNodes);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cleanup.run();
                }
            });
        } else {
            cleanup.run();
        }
    }

    private void removeChunksFromNodes(String fileId, Map<String, List<String>> chunkNodes) {
        int failures = 0;

        for (Map.Entry<String, List<String>> entry : chunkNodes.entrySet()) {
            for (String nodeUrl : entry.getValue()) {
                try {
                    nodeClient.deleteChunk(nodeUrl, entry.getKey());
                } catch (Exception e) {
                    failures++;
                }
            }
        }

        if (failures == 0) {
            eventLogService.info("DELETE", "Deleted fileId=" + fileId + " chunks=" + chunkNodes.size());
        } else {
            eventLogService.warn("DELETE", "Deleted fileId=" + fileId + " chunks=" + chunkNodes.size()
                    + " but " + failures + " replica(s) could not be removed from their nodes");
        }
    }

    private int minReplicaCount(FileMetadataEntity file) {
        return file.getChunks().stream()
                .mapToInt(chunk -> chunk.replicaUrls().size())
                .min()
                .orElse(0);
    }
}

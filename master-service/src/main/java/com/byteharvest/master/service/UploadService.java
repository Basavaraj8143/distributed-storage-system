package com.byteharvest.master.service;

import com.byteharvest.master.client.StorageNodeClient;
import com.byteharvest.master.config.StorageProperties;
import com.byteharvest.master.entity.ChunkMetadataEntity;
import com.byteharvest.master.entity.ChunkReplicaEntity;
import com.byteharvest.master.entity.FileMetadataEntity;
import com.byteharvest.master.exception.InsufficientNodesException;
import com.byteharvest.master.placement.PlacementStrategy;
import com.byteharvest.master.repository.ChunkMetadataRepository;
import com.byteharvest.master.repository.FileMetadataRepository;
import com.byteharvest.master.util.Checksums;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Splits an uploaded file into chunks and stores each chunk on the configured number of nodes. */
@Service
public class UploadService {

    private final StorageProperties properties;
    private final StorageNodeClient nodeClient;
    private final PlacementStrategy placementStrategy;
    private final EventLogService eventLogService;
    private final HeartbeatService heartbeatService;
    private final FileMetadataRepository fileMetadataRepository;
    private final ChunkMetadataRepository chunkMetadataRepository;

    public UploadService(StorageProperties properties,
                         StorageNodeClient nodeClient,
                         PlacementStrategy placementStrategy,
                         EventLogService eventLogService,
                         HeartbeatService heartbeatService,
                         FileMetadataRepository fileMetadataRepository,
                         ChunkMetadataRepository chunkMetadataRepository) {
        this.properties = properties;
        this.nodeClient = nodeClient;
        this.placementStrategy = placementStrategy;
        this.eventLogService = eventLogService;
        this.heartbeatService = heartbeatService;
        this.fileMetadataRepository = fileMetadataRepository;
        this.chunkMetadataRepository = chunkMetadataRepository;
    }

    @Transactional
    public String handleUpload(MultipartFile file) {
        return handleUpload(file, file.getOriginalFilename());
    }

    @Transactional
    public String handleUpload(MultipartFile file, String filename) {
        try {
            byte[] fileBytes = file.getBytes();
            int chunkSize = properties.chunkSizeBytes();

            String fileId = UUID.randomUUID().toString();

            FileMetadataEntity fileEntity = new FileMetadataEntity();
            fileEntity.setFileId(fileId);
            fileEntity.setOriginalFilename(filename);
            fileEntity = fileMetadataRepository.save(fileEntity);

            int chunkIndex = 0;

            for (int i = 0; i < fileBytes.length; i += chunkSize) {

                int end = Math.min(fileBytes.length, i + chunkSize);
                byte[] chunk = Arrays.copyOfRange(fileBytes, i, end);

                String chunkId = UUID.randomUUID().toString();
                String checksum = Checksums.sha256(chunk);
                List<String> selectedNodes = storeChunkOnReplicas(chunkId, chunk);

                ChunkMetadataEntity chunkEntity = new ChunkMetadataEntity();
                chunkEntity.setChunkId(chunkId);
                chunkEntity.setChunkIndex(chunkIndex);
                chunkEntity.setChecksum(checksum);
                chunkEntity.setFileMetadata(fileEntity);

                for (String nodeUrl : selectedNodes) {
                    ChunkReplicaEntity replicaEntity = new ChunkReplicaEntity();
                    replicaEntity.setNodeUrl(nodeUrl);
                    replicaEntity.setChunkMetadata(chunkEntity);
                    chunkEntity.getReplicas().add(replicaEntity);
                }

                chunkMetadataRepository.save(chunkEntity);

                chunkIndex++;
            }

            eventLogService.info("UPLOAD", "Upload complete fileId=" + fileId + " chunks=" + chunkIndex);

            return fileId;

        } catch (InsufficientNodesException e) {
            eventLogService.error("UPLOAD", "Upload failed: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            eventLogService.error("UPLOAD", "Upload failed: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Stores the chunk on storage.replication-factor nodes. Candidates are the configured nodes that are
     * currently sending heartbeats; the PlacementStrategy picks which one gets each copy. If a send fails
     * (for example the node died within the heartbeat timeout), that node is dropped from the candidates
     * and the strategy is asked again.
     *
     * @return the nodes that now hold the chunk
     */
    private List<String> storeChunkOnReplicas(String chunkId, byte[] chunk) {
        Set<String> activeNodeUrls = heartbeatService.getActiveNodeUrls();

        List<String> candidates = properties.nodes().stream()
                .filter(activeNodeUrls::contains)
                .collect(Collectors.toCollection(ArrayList::new));

        List<String> stored = new ArrayList<>();
        while (stored.size() < properties.replicationFactor()) {
            Optional<String> next = placementStrategy.chooseNext(candidates, Collections.unmodifiableList(stored));
            if (next.isEmpty()) {
                break;
            }
            String nodeUrl = next.get();
            candidates.remove(nodeUrl);
            try {
                nodeClient.storeChunk(nodeUrl, chunkId, chunk);
                stored.add(nodeUrl);
            } catch (Exception e) {
                eventLogService.warn("UPLOAD", "Could not store chunk " + chunkId + " on " + nodeUrl + ", trying another node");
            }
        }

        if (stored.size() < properties.replicationFactor()) {
            throw new InsufficientNodesException("Only " + stored.size() + " of " + properties.replicationFactor()
                    + " required storage nodes are available. Start more storage nodes and try again.");
        }

        return stored;
    }
}

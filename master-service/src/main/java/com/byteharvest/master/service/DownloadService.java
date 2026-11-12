package com.byteharvest.master.service;

import com.byteharvest.master.client.StorageNodeClient;
import com.byteharvest.master.entity.ChunkMetadataEntity;
import com.byteharvest.master.entity.ChunkReplicaEntity;
import com.byteharvest.master.entity.FileMetadataEntity;
import com.byteharvest.master.exception.ChunkUnavailableException;
import com.byteharvest.master.exception.StoredFileNotFoundException;
import com.byteharvest.master.repository.ChunkMetadataRepository;
import com.byteharvest.master.repository.FileMetadataRepository;
import com.byteharvest.master.util.Checksums;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** Reassembles a file from its chunks, verifying every chunk against its checksum on the way. */
@Service
public class DownloadService {

    private final StorageNodeClient nodeClient;
    private final EventLogService eventLogService;
    private final FileMetadataRepository fileMetadataRepository;
    private final ChunkMetadataRepository chunkMetadataRepository;

    public DownloadService(StorageNodeClient nodeClient,
                           EventLogService eventLogService,
                           FileMetadataRepository fileMetadataRepository,
                           ChunkMetadataRepository chunkMetadataRepository) {
        this.nodeClient = nodeClient;
        this.eventLogService = eventLogService;
        this.fileMetadataRepository = fileMetadataRepository;
        this.chunkMetadataRepository = chunkMetadataRepository;
    }

    @Transactional(readOnly = true)
    public byte[] download(String fileId) {
        try {
            Optional<FileMetadataEntity> fileOpt = fileMetadataRepository.findById(fileId);
            if (fileOpt.isEmpty()) {
                throw new StoredFileNotFoundException(fileId);
            }

            List<ChunkMetadataEntity> chunks = fileOpt.get().getChunks();
            chunks.sort(Comparator.comparingInt(ChunkMetadataEntity::getChunkIndex));

            ByteArrayOutputStream output = new ByteArrayOutputStream();

            for (ChunkMetadataEntity chunk : chunks) {
                byte[] data = fetchChunkFromReplicas(chunk);
                output.write(data);
            }
            eventLogService.info("DOWNLOAD", "Download complete fileId=" + fileId + " chunks=" + chunks.size());

            return output.toByteArray();

        } catch (StoredFileNotFoundException e) {
            eventLogService.warn("DOWNLOAD", "Download requested for unknown fileId=" + fileId);
            throw e;
        } catch (ChunkUnavailableException e) {
            eventLogService.error("DOWNLOAD", "Download failed fileId=" + fileId + " reason=" + e.getMessage());
            throw e;
        } catch (Exception e) {
            eventLogService.error("DOWNLOAD", "Download failed fileId=" + fileId + " reason=" + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Reads the chunk from its replicas until one passes the checksum. Replicas found corrupt on the way are
     * overwritten from the good copy; if that overwrite fails, the replica record is dropped.
     */
    private byte[] fetchChunkFromReplicas(ChunkMetadataEntity chunk) {
        String chunkId = chunk.getChunkId();
        String expectedChecksum = chunk.getChecksum();
        List<String> nodeUrls = chunk.getReplicas().stream()
                .map(ChunkReplicaEntity::getNodeUrl)
                .collect(Collectors.toList());

        List<String> corruptedNodes = new ArrayList<>();
        byte[] validData = null;

        for (String nodeUrl : nodeUrls) {
            try {
                byte[] data = nodeClient.fetchChunk(nodeUrl, chunkId);
                if (data != null) {
                    String actualChecksum = Checksums.sha256(data);
                    if (actualChecksum.equals(expectedChecksum)) {
                        validData = data;
                        break;
                    } else {
                        eventLogService.warn("INTEGRITY", "Corrupted chunk detected on node " + nodeUrl + " for chunk " + chunkId);
                        corruptedNodes.add(nodeUrl);
                    }
                }
            } catch (Exception e) {
                // Node might be unreachable, try next replica
            }
        }

        if (validData != null) {
            for (String corruptNodeUrl : corruptedNodes) {
                try {
                    nodeClient.storeChunk(corruptNodeUrl, chunkId, validData);
                    eventLogService.info("INTEGRITY", "Repaired corrupted chunk " + chunkId + " on node " + corruptNodeUrl);
                } catch (Exception e) {
                    Iterator<ChunkReplicaEntity> it = chunk.getReplicas().iterator();
                    while (it.hasNext()) {
                        if (it.next().getNodeUrl().equals(corruptNodeUrl)) {
                            it.remove();
                            break;
                        }
                    }
                    chunkMetadataRepository.save(chunk);
                    eventLogService.warn("INTEGRITY", "Failed to overwrite corrupted chunk on " + corruptNodeUrl + ", removed replica reference");
                }
            }
            return validData;
        }

        throw new ChunkUnavailableException("All replicas failed or were corrupted for chunk: " + chunkId);
    }
}

package com.byteharvest.master.service;

import com.byteharvest.master.client.StorageNodeClient;
import com.byteharvest.master.config.StorageProperties;
import com.byteharvest.master.entity.ChunkMetadataEntity;
import com.byteharvest.master.entity.ChunkReplicaEntity;
import com.byteharvest.master.model.ReconcileResult;
import com.byteharvest.master.repository.ChunkMetadataRepository;
import com.byteharvest.master.util.Checksums;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The repair operations: drop replicas of failed nodes, fix corrupted copies, add missing copies, and reconcile
 * a node that has come back. ReplicationRepairService decides when these run; this class does the work.
 */
@Service
public class ChunkRepairService {

    private final StorageProperties properties;
    private final StorageNodeClient nodeClient;
    private final EventLogService eventLogService;
    private final ChunkMetadataRepository chunkMetadataRepository;

    public ChunkRepairService(StorageProperties properties,
                              StorageNodeClient nodeClient,
                              EventLogService eventLogService,
                              ChunkMetadataRepository chunkMetadataRepository) {
        this.properties = properties;
        this.nodeClient = nodeClient;
        this.eventLogService = eventLogService;
        this.chunkMetadataRepository = chunkMetadataRepository;
    }

    @Transactional
    public int removeFailedNodeReferences(Set<String> failedNodeUrls) {
        if (failedNodeUrls == null || failedNodeUrls.isEmpty()) {
            return 0;
        }

        int updatedChunks = 0;
        List<ChunkMetadataEntity> allChunks = chunkMetadataRepository.findAll();

        for (ChunkMetadataEntity chunk : allChunks) {
            boolean modified = false;
            Iterator<ChunkReplicaEntity> it = chunk.getReplicas().iterator();
            while (it.hasNext()) {
                ChunkReplicaEntity replica = it.next();
                if (failedNodeUrls.contains(replica.getNodeUrl())) {
                    it.remove();
                    modified = true;
                }
            }
            if (modified) {
                chunkMetadataRepository.save(chunk);
                updatedChunks++;
            }
        }

        return updatedChunks;
    }

    @Transactional
    public int repairUnderReplicatedChunks(Set<String> activeNodeUrls) {
        if (activeNodeUrls == null || activeNodeUrls.isEmpty()) {
            return 0;
        }

        int repairedReplicas = 0;
        List<ChunkMetadataEntity> allChunks = chunkMetadataRepository.findAll();

        for (ChunkMetadataEntity chunk : allChunks) {
            List<String> replicas = chunk.replicaUrls();

            if (replicas.size() >= properties.replicationFactor()) {
                continue;
            }

            byte[] chunkData = tryFetchChunk(chunk.getChunkId(), replicas);
            if (chunkData == null) {
                continue;
            }

            Set<String> candidateTargets = new LinkedHashSet<>(activeNodeUrls);
            candidateTargets.removeAll(replicas);

            while (replicas.size() < properties.replicationFactor() && !candidateTargets.isEmpty()) {
                String targetNode = candidateTargets.iterator().next();
                candidateTargets.remove(targetNode);

                try {
                    nodeClient.storeChunk(targetNode, chunk.getChunkId(), chunkData);
                    replicas.add(targetNode);

                    ChunkReplicaEntity newReplica = new ChunkReplicaEntity();
                    newReplica.setNodeUrl(targetNode);
                    newReplica.setChunkMetadata(chunk);
                    chunk.getReplicas().add(newReplica);

                    repairedReplicas++;
                } catch (Exception ignored) {
                    // Best effort: try another target.
                }
            }

            chunkMetadataRepository.save(chunk);
        }

        return repairedReplicas;
    }

    @Transactional
    public int verifyAndRepairCorruptions(Set<String> activeNodeUrls) {
        if (activeNodeUrls == null || activeNodeUrls.isEmpty()) {
            return 0;
        }

        int repairedCount = 0;
        List<ChunkMetadataEntity> allChunks = chunkMetadataRepository.findAll();

        for (ChunkMetadataEntity chunk : allChunks) {
            String chunkId = chunk.getChunkId();
            String expectedChecksum = chunk.getChecksum();
            List<String> replicas = chunk.getReplicas().stream()
                    .map(ChunkReplicaEntity::getNodeUrl)
                    .collect(Collectors.toList());

            List<String> corruptedNodes = new ArrayList<>();
            byte[] validData = null;

            for (String nodeUrl : replicas) {
                if (!activeNodeUrls.contains(nodeUrl)) {
                    continue;
                }

                try {
                    byte[] data = nodeClient.fetchChunk(nodeUrl, chunkId);
                    if (data != null) {
                        String actualChecksum = Checksums.sha256(data);
                        if (actualChecksum.equals(expectedChecksum)) {
                            if (validData == null) {
                                validData = data;
                            }
                        } else {
                            corruptedNodes.add(nodeUrl);
                        }
                    } else {
                        corruptedNodes.add(nodeUrl);
                    }
                } catch (Exception e) {
                    // ignore/skip
                }
            }

            if (!corruptedNodes.isEmpty()) {
                if (validData == null) {
                    for (String nodeUrl : replicas) {
                        try {
                            byte[] data = nodeClient.fetchChunk(nodeUrl, chunkId);
                            if (data != null && Checksums.sha256(data).equals(expectedChecksum)) {
                                validData = data;
                                break;
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }

                if (validData != null) {
                    for (String corruptNodeUrl : corruptedNodes) {
                        try {
                            nodeClient.storeChunk(corruptNodeUrl, chunkId, validData);
                            eventLogService.info("INTEGRITY", "Scheduled repair: fixed corrupted chunk " + chunkId + " on node " + corruptNodeUrl);
                            repairedCount++;
                        } catch (Exception e) {
                            Iterator<ChunkReplicaEntity> it = chunk.getReplicas().iterator();
                            while (it.hasNext()) {
                                if (it.next().getNodeUrl().equals(corruptNodeUrl)) {
                                    it.remove();
                                    break;
                                }
                            }
                            chunkMetadataRepository.save(chunk);
                            eventLogService.warn("INTEGRITY", "Scheduled repair: failed to overwrite corrupted chunk on " + corruptNodeUrl + ", removed replica reference");
                            repairedCount++;
                        }
                    }
                } else {
                    eventLogService.error("INTEGRITY", "CRITICAL: All replicas for chunk " + chunkId + " are corrupted or unreachable!");
                }
            }
        }

        return repairedCount;
    }

    /**
     * Compares the chunks a node actually holds with the metadata. Used when a node comes back after being
     * FAILED (its replica records were dropped) and when a node is first seen after a master start.
     *
     * <ul>
     *   <li>Known chunk, already recorded for this node: nothing to do.</li>
     *   <li>Known chunk, enough healthy replicas elsewhere: the copy is surplus and is deleted.</li>
     *   <li>Known chunk, not enough healthy replicas: the copy is checksum-verified and, if valid,
     *       its replica record is re-created (adopted) instead of copying the chunk again.</li>
     *   <li>Unknown chunk: left in place and only counted. It may be a file deleted while the node was away,
     *       or an upload still in progress, and the node's chunk list carries no age to tell them apart.</li>
     * </ul>
     *
     * A failure to list the node's chunks throws, so the caller can retry. A failure on one chunk only
     * skips that chunk.
     */
    public ReconcileResult reconcileNode(String nodeUrl, Set<String> activeNodeUrls) {
        List<String> reported = nodeClient.listChunkIds(nodeUrl);

        int adopted = 0;
        int surplusRemoved = 0;
        int corruptRemoved = 0;
        int unknownKept = 0;
        int skipped = 0;

        for (String reportedChunkId : reported) {
            try {
                ChunkMetadataEntity chunk = chunkMetadataRepository.findByChunkId(reportedChunkId);

                if (chunk == null) {
                    unknownKept++;
                    continue;
                }

                List<String> replicas = chunk.replicaUrls();
                if (replicas.contains(nodeUrl)) {
                    continue;
                }

                long healthyElsewhere = replicas.stream().filter(activeNodeUrls::contains).count();
                if (healthyElsewhere >= properties.replicationFactor()) {
                    if (deleteChunkOnNode(nodeUrl, chunk.getChunkId())) {
                        surplusRemoved++;
                    }
                } else if (nodeCopyMatchesChecksum(nodeUrl, chunk)) {
                    ChunkReplicaEntity replica = new ChunkReplicaEntity();
                    replica.setNodeUrl(nodeUrl);
                    replica.setChunkMetadata(chunk);
                    chunk.getReplicas().add(replica);
                    chunkMetadataRepository.save(chunk);
                    adopted++;
                } else if (deleteChunkOnNode(nodeUrl, chunk.getChunkId())) {
                    corruptRemoved++;
                }
            } catch (Exception e) {
                skipped++;
            }
        }

        return new ReconcileResult(adopted, surplusRemoved, corruptRemoved, unknownKept, skipped);
    }

    private boolean deleteChunkOnNode(String nodeUrl, String chunkId) {
        try {
            nodeClient.deleteChunk(nodeUrl, chunkId);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Fetches the node's copy and compares its checksum with the one recorded at upload.
     * A fetch failure throws (the chunk is skipped); only a successful read with a mismatch returns false.
     */
    private boolean nodeCopyMatchesChecksum(String nodeUrl, ChunkMetadataEntity chunk) {
        byte[] data = nodeClient.fetchChunk(nodeUrl, chunk.getChunkId());
        return data != null && Checksums.sha256(data).equals(chunk.getChecksum());
    }

    private byte[] tryFetchChunk(String chunkId, List<String> nodeUrls) {
        for (String nodeUrl : nodeUrls) {
            try {
                byte[] data = nodeClient.fetchChunk(nodeUrl, chunkId);
                if (data != null) {
                    return data;
                }
            } catch (Exception ignored) {
                // Try next replica
            }
        }
        return null;
    }
}

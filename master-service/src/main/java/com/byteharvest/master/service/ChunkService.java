package com.byteharvest.master.service;

import com.byteharvest.master.entity.ChunkMetadataEntity;
import com.byteharvest.master.entity.ChunkReplicaEntity;
import com.byteharvest.master.entity.FileMetadataEntity;
import com.byteharvest.master.exception.ChunkUnavailableException;
import com.byteharvest.master.exception.InsufficientNodesException;
import com.byteharvest.master.exception.StoredFileNotFoundException;
import com.byteharvest.master.model.ChunkInfo;
import com.byteharvest.master.model.FileDetails;
import com.byteharvest.master.model.FileSummary;
import com.byteharvest.master.model.ReconcileResult;
import com.byteharvest.master.repository.ChunkMetadataRepository;
import com.byteharvest.master.repository.ChunkReplicaRepository;
import com.byteharvest.master.repository.FileMetadataRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ChunkService {

    private static final int REPLICATION_FACTOR = 2;

    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private EventLogService eventLogService;
    @Autowired
    private HeartbeatService heartbeatService;
    
    @Autowired
    private FileMetadataRepository fileMetadataRepository;
    @Autowired
    private ChunkMetadataRepository chunkMetadataRepository;
    @Autowired
    private ChunkReplicaRepository chunkReplicaRepository;

    @Value("${storage.nodes:http://localhost:5001,http://localhost:5002,http://localhost:5003,http://localhost:5004,http://localhost:5005}")
    private String configuredNodes;

    private List<String> nodes;

    @PostConstruct
    public void initNodes() {
        List<String> parsedNodes = Arrays.stream(configuredNodes.split(","))
                .map(String::trim)
                .filter(node -> !node.isEmpty())
                .toList();

        if (parsedNodes.isEmpty()) {
            throw new RuntimeException("No storage nodes configured");
        }

        nodes = parsedNodes;
    }

    @Transactional
    public String handleUpload(MultipartFile file) {
        return handleUpload(file, file.getOriginalFilename());
    }

    @Transactional
    public String handleUpload(MultipartFile file, String filename) {
        try {
            byte[] fileBytes = file.getBytes();
            int chunkSize = 1024 * 1024;

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
                String checksum = calculateSha256(chunk);
                List<String> selectedNodes = storeChunkOnReplicas(chunkId, chunk, chunkIndex);

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

    @Transactional(readOnly = true)
    public String getOriginalFileName(String fileId) {
        Optional<FileMetadataEntity> opt = fileMetadataRepository.findById(fileId);
        return opt.map(FileMetadataEntity::getOriginalFilename).orElse(null);
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
            List<String> replicas = chunk.getReplicas().stream()
                    .map(ChunkReplicaEntity::getNodeUrl)
                    .distinct()
                    .collect(Collectors.toList());

            if (replicas.size() >= REPLICATION_FACTOR) {
                continue;
            }

            byte[] chunkData = tryFetchChunk(chunk.getChunkId(), replicas);
            if (chunkData == null) {
                continue;
            }

            Set<String> candidateTargets = new LinkedHashSet<>(activeNodeUrls);
            candidateTargets.removeAll(replicas);

            while (replicas.size() < REPLICATION_FACTOR && !candidateTargets.isEmpty()) {
                String targetNode = candidateTargets.iterator().next();
                candidateTargets.remove(targetNode);

                try {
                    sendChunkToNode(targetNode, chunk.getChunkId(), chunkData);
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

    @Transactional(readOnly = true)
    public List<FileSummary> listFiles() {
        return fileMetadataRepository.findAll().stream()
                .map(file -> new FileSummary(
                        file.getFileId(),
                        file.getOriginalFilename(),
                        file.getChunks().size(),
                        minReplicaCount(file),
                        REPLICATION_FACTOR))
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
                        replicaUrls(chunk)))
                .collect(Collectors.toList());

        return new FileDetails(file.getFileId(), file.getOriginalFilename(), chunks.size(), REPLICATION_FACTOR, chunks);
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
            chunkNodes.put(chunk.getChunkId(), replicaUrls(chunk));
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
                    restTemplate.delete(nodeUrl + "/deleteChunk/" + entry.getKey());
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

    private List<String> replicaUrls(ChunkMetadataEntity chunk) {
        return chunk.getReplicas().stream()
                .map(ChunkReplicaEntity::getNodeUrl)
                .distinct()
                .collect(Collectors.toList());
    }

    private int minReplicaCount(FileMetadataEntity file) {
        return file.getChunks().stream()
                .mapToInt(chunk -> replicaUrls(chunk).size())
                .min()
                .orElse(0);
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
        String[] reported = restTemplate.getForObject(nodeUrl + "/chunks", String[].class);
        if (reported == null) {
            return ReconcileResult.EMPTY;
        }

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

                List<String> replicas = replicaUrls(chunk);
                if (replicas.contains(nodeUrl)) {
                    continue;
                }

                long healthyElsewhere = replicas.stream().filter(activeNodeUrls::contains).count();
                if (healthyElsewhere >= REPLICATION_FACTOR) {
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
            restTemplate.delete(nodeUrl + "/deleteChunk/" + chunkId);
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
        byte[] data = restTemplate.getForObject(nodeUrl + "/getChunk/" + chunk.getChunkId(), byte[].class);
        return data != null && calculateSha256(data).equals(chunk.getChecksum());
    }

    private void sendChunkToNode(String nodeUrl, String chunkId, byte[] chunk) {
        String url = nodeUrl + "/storeChunk";

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

        body.add("chunkId", chunkId);
        body.add("file", new ByteArrayResource(chunk) {
            @Override
            public String getFilename() {
                return "chunk";
            }
        });

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpEntity<MultiValueMap<String, Object>> request =
                new HttpEntity<>(body, headers);

        restTemplate.postForEntity(url, request, String.class);
    }

    /**
     * Stores the chunk on REPLICATION_FACTOR nodes. Candidates are the configured nodes that are
     * currently sending heartbeats, in random order. If a send fails (for example the node died
     * within the heartbeat timeout), the next candidate is tried instead.
     *
     * @return the nodes that now hold the chunk
     */
    private List<String> storeChunkOnReplicas(String chunkId, byte[] chunk, int chunkIndex) {
        Set<String> activeNodeUrls = heartbeatService.getActiveNodeUrls();

        List<String> candidates = nodes.stream()
                .filter(activeNodeUrls::contains)
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(candidates, new Random(chunkIndex + System.nanoTime()));

        List<String> stored = new ArrayList<>();
        for (String nodeUrl : candidates) {
            if (stored.size() == REPLICATION_FACTOR) {
                break;
            }
            try {
                sendChunkToNode(nodeUrl, chunkId, chunk);
                stored.add(nodeUrl);
            } catch (Exception e) {
                eventLogService.warn("UPLOAD", "Could not store chunk " + chunkId + " on " + nodeUrl + ", trying another node");
            }
        }

        if (stored.size() < REPLICATION_FACTOR) {
            throw new InsufficientNodesException("Only " + stored.size() + " of " + REPLICATION_FACTOR
                    + " required storage nodes are available. Start more storage nodes and try again.");
        }

        return stored;
    }

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
                String url = nodeUrl + "/getChunk/" + chunkId;
                byte[] data = restTemplate.getForObject(url, byte[].class);
                if (data != null) {
                    String actualChecksum = calculateSha256(data);
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
                    sendChunkToNode(corruptNodeUrl, chunkId, validData);
                    eventLogService.info("INTEGRITY", "Repaired corrupted chunk " + chunkId + " on node " + corruptNodeUrl);
                } catch (Exception e) {
                    Iterator<ChunkReplicaEntity> it = chunk.getReplicas().iterator();
                    while(it.hasNext()){
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

    private byte[] tryFetchChunk(String chunkId, List<String> nodeUrls) {
        for (String nodeUrl : nodeUrls) {
            try {
                String url = nodeUrl + "/getChunk/" + chunkId;
                byte[] data = restTemplate.getForObject(url, byte[].class);
                if (data != null) {
                    return data;
                }
            } catch (Exception ignored) {
                // Try next replica
            }
        }
        return null;
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
                    String url = nodeUrl + "/getChunk/" + chunkId;
                    byte[] data = restTemplate.getForObject(url, byte[].class);
                    if (data != null) {
                        String actualChecksum = calculateSha256(data);
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
                            String url = nodeUrl + "/getChunk/" + chunkId;
                            byte[] data = restTemplate.getForObject(url, byte[].class);
                            if (data != null && calculateSha256(data).equals(expectedChecksum)) {
                                validData = data;
                                break;
                            }
                        } catch (Exception ignored) {}
                    }
                }

                if (validData != null) {
                    for (String corruptNodeUrl : corruptedNodes) {
                        try {
                            sendChunkToNode(corruptNodeUrl, chunkId, validData);
                            eventLogService.info("INTEGRITY", "Scheduled repair: fixed corrupted chunk " + chunkId + " on node " + corruptNodeUrl);
                            repairedCount++;
                        } catch (Exception e) {
                            Iterator<ChunkReplicaEntity> it = chunk.getReplicas().iterator();
                            while(it.hasNext()){
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

    private String calculateSha256(byte[] data) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }
}

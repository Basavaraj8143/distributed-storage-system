package com.byteharvest.master.service;

import com.byteharvest.master.entity.ChunkMetadataEntity;
import com.byteharvest.master.entity.ChunkReplicaEntity;
import com.byteharvest.master.entity.FileMetadataEntity;
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
        try {
            byte[] fileBytes = file.getBytes();
            int chunkSize = 1024 * 1024;

            String fileId = UUID.randomUUID().toString();
            
            FileMetadataEntity fileEntity = new FileMetadataEntity();
            fileEntity.setFileId(fileId);
            fileEntity.setOriginalFilename(file.getOriginalFilename());
            fileEntity = fileMetadataRepository.save(fileEntity);

            int chunkIndex = 0;

            for (int i = 0; i < fileBytes.length; i += chunkSize) {

                int end = Math.min(fileBytes.length, i + chunkSize);
                byte[] chunk = Arrays.copyOfRange(fileBytes, i, end);

                String chunkId = UUID.randomUUID().toString();
                String checksum = calculateSha256(chunk);
                List<String> selectedNodes = selectNodesForReplication(chunkIndex);

                for (String nodeUrl : selectedNodes) {
                    sendChunkToNode(nodeUrl, chunkId, chunk);
                }

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
                throw new RuntimeException("File not found");
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

    private List<String> selectNodesForReplication(int chunkIndex) {
        if (nodes.size() < REPLICATION_FACTOR) {
            throw new RuntimeException("Not enough nodes for replication factor " + REPLICATION_FACTOR);
        }

        List<String> shuffledNodes = new ArrayList<>(nodes);
        Collections.shuffle(shuffledNodes, new Random(chunkIndex + System.nanoTime()));

        return new ArrayList<>(shuffledNodes.subList(0, REPLICATION_FACTOR));
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

        throw new RuntimeException("All replicas failed or were corrupted for chunk: " + chunkId);
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

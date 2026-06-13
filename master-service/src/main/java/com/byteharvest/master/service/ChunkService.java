package com.byteharvest.master.service;

import com.byteharvest.master.model.ChunkMetadata;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
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

    private Map<String, List<ChunkMetadata>> storage = new HashMap<>();

    @Value("${storage.nodes:http://localhost:5001,http://localhost:5002,http://localhost:5003}")
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

    public synchronized String handleUpload(MultipartFile file) {
        try {
            byte[] fileBytes = file.getBytes();
            int chunkSize = 1024 * 1024;

            String fileId = UUID.randomUUID().toString();
            List<ChunkMetadata> chunkList = new ArrayList<>();

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

                chunkList.add(new ChunkMetadata(chunkId, chunkIndex, selectedNodes, checksum));

                chunkIndex++;
            }

            storage.put(fileId, chunkList);
            eventLogService.info("UPLOAD", "Upload complete fileId=" + fileId + " chunks=" + chunkList.size());

            return fileId;

        } catch (Exception e) {
            eventLogService.error("UPLOAD", "Upload failed: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public synchronized byte[] download(String fileId) {
        try {
            List<ChunkMetadata> chunks = storage.get(fileId);

            if (chunks == null) {
                throw new RuntimeException("File not found");
            }

            chunks.sort(Comparator.comparingInt(ChunkMetadata::getChunkIndex));

            ByteArrayOutputStream output = new ByteArrayOutputStream();

            for (ChunkMetadata chunk : chunks) {
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

    public synchronized int removeFailedNodeReferences(Set<String> failedNodeUrls) {
        if (failedNodeUrls == null || failedNodeUrls.isEmpty()) {
            return 0;
        }

        int updatedChunks = 0;
        for (List<ChunkMetadata> chunks : storage.values()) {
            for (ChunkMetadata chunk : chunks) {
                List<String> currentUrls = new ArrayList<>(chunk.getNodeUrls());
                List<String> filteredUrls = currentUrls.stream()
                        .filter(nodeUrl -> !failedNodeUrls.contains(nodeUrl))
                        .distinct()
                        .collect(Collectors.toCollection(ArrayList::new));

                if (!filteredUrls.equals(currentUrls)) {
                    chunk.setNodeUrls(filteredUrls);
                    updatedChunks++;
                }
            }
        }

        return updatedChunks;
    }

    public synchronized int repairUnderReplicatedChunks(Set<String> activeNodeUrls) {
        if (activeNodeUrls == null || activeNodeUrls.isEmpty()) {
            return 0;
        }

        int repairedReplicas = 0;

        for (List<ChunkMetadata> chunks : storage.values()) {
            for (ChunkMetadata chunk : chunks) {
                List<String> replicas = new ArrayList<>(new LinkedHashSet<>(chunk.getNodeUrls()));

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
                        repairedReplicas++;
                    } catch (Exception ignored) {
                        // Best effort: try another target.
                    }
                }

                chunk.setNodeUrls(replicas);
            }
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

    private byte[] fetchChunkFromReplicas(ChunkMetadata chunk) {
        String chunkId = chunk.getChunkId();
        String expectedChecksum = chunk.getChecksum();
        List<String> nodeUrls = new ArrayList<>(chunk.getNodeUrls());
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
                    List<String> updatedNodeUrls = new ArrayList<>(chunk.getNodeUrls());
                    updatedNodeUrls.remove(corruptNodeUrl);
                    chunk.setNodeUrls(updatedNodeUrls);
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

    public synchronized int verifyAndRepairCorruptions(Set<String> activeNodeUrls) {
        if (activeNodeUrls == null || activeNodeUrls.isEmpty()) {
            return 0;
        }

        int repairedCount = 0;

        for (List<ChunkMetadata> chunks : storage.values()) {
            for (ChunkMetadata chunk : chunks) {
                String chunkId = chunk.getChunkId();
                String expectedChecksum = chunk.getChecksum();
                List<String> replicas = new ArrayList<>(chunk.getNodeUrls());
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
                                List<String> updatedNodeUrls = new ArrayList<>(chunk.getNodeUrls());
                                updatedNodeUrls.remove(corruptNodeUrl);
                                chunk.setNodeUrls(updatedNodeUrls);
                                eventLogService.warn("INTEGRITY", "Scheduled repair: failed to overwrite corrupted chunk on " + corruptNodeUrl + ", removed replica reference");
                                repairedCount++;
                            }
                        }
                    } else {
                        eventLogService.error("INTEGRITY", "CRITICAL: All replicas for chunk " + chunkId + " are corrupted or unreachable!");
                    }
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

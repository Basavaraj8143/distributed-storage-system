package com.byteharvest.master.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class HeartbeatService {

    private static final Logger logger = LoggerFactory.getLogger(HeartbeatService.class);
    private static final long NODE_TIMEOUT_MS = 15000;

    private final Map<String, Long> nodeLastSeen = new ConcurrentHashMap<>();
    private final Set<String> activeNodes = ConcurrentHashMap.newKeySet();
    private final Set<String> failedNodes = ConcurrentHashMap.newKeySet();
    private final Map<String, String> nodeIdToUrl = new ConcurrentHashMap<>();
    private final Map<String, String> nodeUrlToId = new ConcurrentHashMap<>();
    private final EventLogService eventLogService;

    public HeartbeatService(EventLogService eventLogService) {
        this.eventLogService = eventLogService;
    }

    public void recordHeartbeat(String nodeId, String nodeUrl) {
        boolean wasFailed = failedNodes.contains(nodeId);
        nodeLastSeen.put(nodeId, System.currentTimeMillis());
        String previousUrl = nodeIdToUrl.put(nodeId, nodeUrl);
        if (previousUrl != null && !previousUrl.equals(nodeUrl)) {
            nodeUrlToId.remove(previousUrl);
        }
        nodeUrlToId.put(nodeUrl, nodeId);
        activeNodes.add(nodeId);
        failedNodes.remove(nodeId);

        if (wasFailed) {
            eventLogService.info("HEARTBEAT", "Node RECOVERED: " + nodeId + " (" + nodeUrl + ")");
        }
    }

    public Map<String, Long> getNodeLastSeen() {
        return Collections.unmodifiableMap(nodeLastSeen);
    }

    public Set<String> getActiveNodes() {
        return Collections.unmodifiableSet(activeNodes);
    }

    public boolean isNodeAlive(String nodeId) {
        return activeNodes.contains(nodeId);
    }

    public Set<String> getFailedNodes() {
        return Collections.unmodifiableSet(failedNodes);
    }

    public Set<String> getActiveNodeUrls() {
        Set<String> activeNodeUrls = ConcurrentHashMap.newKeySet();
        for (String nodeId : activeNodes) {
            String nodeUrl = nodeIdToUrl.get(nodeId);
            if (nodeUrl != null && !nodeUrl.isBlank()) {
                activeNodeUrls.add(nodeUrl);
            }
        }
        return Collections.unmodifiableSet(activeNodeUrls);
    }

    public Set<String> getFailedNodeUrls() {
        Set<String> failedNodeUrls = ConcurrentHashMap.newKeySet();
        for (String nodeId : failedNodes) {
            String nodeUrl = nodeIdToUrl.get(nodeId);
            if (nodeUrl != null && !nodeUrl.isBlank()) {
                failedNodeUrls.add(nodeUrl);
            }
        }
        return Collections.unmodifiableSet(failedNodeUrls);
    }

    public void markNodeFailed(String nodeId) {
        activeNodes.remove(nodeId);
        failedNodes.add(nodeId);
    }

    @Scheduled(fixedRate = 10000)
    public void checkNodeFailures() {
        long now = System.currentTimeMillis();

        for (Map.Entry<String, Long> entry : nodeLastSeen.entrySet()) {
            String nodeId = entry.getKey();
            long lastSeen = entry.getValue();

            if (activeNodes.contains(nodeId) && (now - lastSeen > NODE_TIMEOUT_MS)) {
                markNodeFailed(nodeId);
                logger.warn("Node FAILED: {}", nodeId);
                eventLogService.warn("HEARTBEAT", "Node FAILED: " + nodeId);
            }
        }
    }

    public Instant getLastSeenAsInstant(String nodeId) {
        Long timestamp = nodeLastSeen.get(nodeId);
        if (timestamp == null) {
            return null;
        }
        return Instant.ofEpochMilli(timestamp);
    }

    public Long getLastSeenMillis(String nodeId) {
        return nodeLastSeen.get(nodeId);
    }

    public String getNodeUrl(String nodeId) {
        return nodeIdToUrl.get(nodeId);
    }

    public List<String> getKnownNodeIds() {
        Set<String> merged = ConcurrentHashMap.newKeySet();
        merged.addAll(nodeIdToUrl.keySet());
        merged.addAll(nodeLastSeen.keySet());
        return new ArrayList<>(merged);
    }
}

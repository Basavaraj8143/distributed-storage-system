package com.byteharvest.master.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Tracks node liveness. A node moves through three states based on how long ago its last heartbeat was:
 *
 * <pre>
 *   ACTIVE  heartbeat within the stale timeout     receives uploads, used as a repair target
 *   STALE   silent for longer than stale timeout   receives NO new uploads, replica records are KEPT
 *   FAILED  silent for longer than dead timeout    replica records are dropped, chunks are re-replicated
 * </pre>
 *
 * A node that comes back from STALE simply becomes ACTIVE again: nothing was moved, so nothing is wasted.
 * A node that comes back from FAILED (or is seen for the first time since the master started) is queued
 * as "rejoined", so its stored chunks can be reconciled with the metadata.
 */
@Service
public class HeartbeatService {

    private static final Logger logger = LoggerFactory.getLogger(HeartbeatService.class);

    private final long staleTimeoutMs;
    private final long deadTimeoutMs;
    private final LongSupplier clock;

    private final Map<String, Long> nodeLastSeen = new ConcurrentHashMap<>();
    private final Set<String> activeNodes = ConcurrentHashMap.newKeySet();
    private final Set<String> staleNodes = ConcurrentHashMap.newKeySet();
    private final Set<String> failedNodes = ConcurrentHashMap.newKeySet();
    private final Map<String, String> nodeIdToUrl = new ConcurrentHashMap<>();
    private final Map<String, String> nodeUrlToId = new ConcurrentHashMap<>();
    private final Set<String> rejoinedNodeUrls = ConcurrentHashMap.newKeySet();
    private final EventLogService eventLogService;

    @Autowired
    public HeartbeatService(EventLogService eventLogService,
                            @Value("${heartbeat.stale-timeout-ms:15000}") long staleTimeoutMs,
                            @Value("${heartbeat.dead-timeout-ms:60000}") long deadTimeoutMs) {
        this(eventLogService, staleTimeoutMs, deadTimeoutMs, System::currentTimeMillis);
    }

    /**
     * Constructor with an injectable clock, so tests can move time without sleeping.
     */
    public HeartbeatService(EventLogService eventLogService, long staleTimeoutMs, long deadTimeoutMs, LongSupplier clock) {
        if (staleTimeoutMs <= 0 || deadTimeoutMs <= staleTimeoutMs) {
            throw new IllegalArgumentException(
                    "heartbeat.dead-timeout-ms (" + deadTimeoutMs + ") must be greater than heartbeat.stale-timeout-ms ("
                            + staleTimeoutMs + "), and both must be positive");
        }
        this.eventLogService = eventLogService;
        this.staleTimeoutMs = staleTimeoutMs;
        this.deadTimeoutMs = deadTimeoutMs;
        this.clock = clock;
    }

    public void recordHeartbeat(String nodeId, String nodeUrl) {
        boolean wasFailed = failedNodes.contains(nodeId);
        boolean wasStale = staleNodes.contains(nodeId);
        boolean firstSeen = !nodeLastSeen.containsKey(nodeId);

        nodeLastSeen.put(nodeId, clock.getAsLong());
        String previousUrl = nodeIdToUrl.put(nodeId, nodeUrl);
        if (previousUrl != null && !previousUrl.equals(nodeUrl)) {
            nodeUrlToId.remove(previousUrl);
        }
        nodeUrlToId.put(nodeUrl, nodeId);
        activeNodes.add(nodeId);
        staleNodes.remove(nodeId);
        failedNodes.remove(nodeId);

        if (wasFailed) {
            eventLogService.info("HEARTBEAT", "Node RECOVERED: " + nodeId + " (" + nodeUrl
                    + "). Its stored chunks will be reconciled with the metadata.");
            rejoinedNodeUrls.add(nodeUrl);
        } else if (wasStale) {
            eventLogService.info("HEARTBEAT", "Node back from STALE: " + nodeId + " (" + nodeUrl
                    + "). No data had been moved.");
        } else if (firstSeen) {
            rejoinedNodeUrls.add(nodeUrl);
        }
    }

    public Map<String, Long> getNodeLastSeen() {
        return Collections.unmodifiableMap(nodeLastSeen);
    }

    public Set<String> getActiveNodes() {
        return Collections.unmodifiableSet(activeNodes);
    }

    public Set<String> getStaleNodes() {
        return Collections.unmodifiableSet(staleNodes);
    }

    public boolean isNodeAlive(String nodeId) {
        return activeNodes.contains(nodeId);
    }

    public Set<String> getFailedNodes() {
        return Collections.unmodifiableSet(failedNodes);
    }

    public Set<String> getActiveNodeUrls() {
        return urlsOf(activeNodes);
    }

    public Set<String> getStaleNodeUrls() {
        return urlsOf(staleNodes);
    }

    public Set<String> getFailedNodeUrls() {
        return urlsOf(failedNodes);
    }

    private Set<String> urlsOf(Set<String> nodeIds) {
        Set<String> urls = ConcurrentHashMap.newKeySet();
        for (String nodeId : nodeIds) {
            String nodeUrl = nodeIdToUrl.get(nodeId);
            if (nodeUrl != null && !nodeUrl.isBlank()) {
                urls.add(nodeUrl);
            }
        }
        return Collections.unmodifiableSet(urls);
    }

    public void markNodeStale(String nodeId) {
        activeNodes.remove(nodeId);
        staleNodes.add(nodeId);
    }

    public void markNodeFailed(String nodeId) {
        activeNodes.remove(nodeId);
        staleNodes.remove(nodeId);
        failedNodes.add(nodeId);
    }

    /**
     * Returns the URLs of nodes that rejoined (back from FAILED, or first seen since the master started)
     * and clears the queue.
     */
    public Set<String> drainRejoinedNodeUrls() {
        Set<String> drained = new HashSet<>();
        Iterator<String> iterator = rejoinedNodeUrls.iterator();
        while (iterator.hasNext()) {
            drained.add(iterator.next());
            iterator.remove();
        }
        return drained;
    }

    /**
     * Puts a node back in the rejoined queue, for example when reconciling it failed and should be retried.
     */
    public void requeueRejoinedNode(String nodeUrl) {
        rejoinedNodeUrls.add(nodeUrl);
    }

    @Scheduled(fixedRate = 5000)
    public void checkNodeFailures() {
        long now = clock.getAsLong();

        for (Map.Entry<String, Long> entry : nodeLastSeen.entrySet()) {
            String nodeId = entry.getKey();
            long silentForMs = now - entry.getValue();

            if (failedNodes.contains(nodeId)) {
                continue;
            }

            if (silentForMs > deadTimeoutMs) {
                markNodeFailed(nodeId);
                logger.warn("Node FAILED: {}", nodeId);
                eventLogService.warn("HEARTBEAT", "Node FAILED: " + nodeId + " (no heartbeat for "
                        + (silentForMs / 1000) + "s). Dropping its replicas and re-replicating.");
            } else if (silentForMs > staleTimeoutMs && activeNodes.contains(nodeId)) {
                markNodeStale(nodeId);
                logger.warn("Node STALE: {}", nodeId);
                eventLogService.warn("HEARTBEAT", "Node STALE: " + nodeId + " (no heartbeat for "
                        + (silentForMs / 1000) + "s). No new uploads; its data is kept for now.");
            }
        }
    }

    public long getStaleAfterMs() {
        return staleTimeoutMs;
    }

    public long getDeadAfterMs() {
        return deadTimeoutMs;
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

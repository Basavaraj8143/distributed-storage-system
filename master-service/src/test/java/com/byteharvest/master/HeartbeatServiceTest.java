package com.byteharvest.master;

import com.byteharvest.master.service.EventLogService;
import com.byteharvest.master.service.HeartbeatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class HeartbeatServiceTest {

    private static final long STALE_MS = 15_000;
    private static final long DEAD_MS = 60_000;
    private static final String NODE_ID = "node-3";
    private static final String NODE_URL = "http://localhost:5003";

    private AtomicLong now;
    private HeartbeatService service;

    @BeforeEach
    void setUp() {
        now = new AtomicLong(1_000_000);
        service = new HeartbeatService(mock(EventLogService.class), STALE_MS, DEAD_MS, now::get);
    }

    private void advanceAndCheck(long millis) {
        now.addAndGet(millis);
        service.checkNodeFailures();
    }

    @Test
    void healthyNodeIsActive() {
        service.recordHeartbeat(NODE_ID, NODE_URL);
        advanceAndCheck(STALE_MS - 1_000);

        assertEquals(Set.of(NODE_URL), service.getActiveNodeUrls());
        assertTrue(service.getStaleNodeUrls().isEmpty());
        assertTrue(service.getFailedNodeUrls().isEmpty());
    }

    @Test
    void silentNodeBecomesStaleAndStopsReceivingUploads() {
        service.recordHeartbeat(NODE_ID, NODE_URL);
        advanceAndCheck(STALE_MS + 1_000);

        assertTrue(service.getActiveNodeUrls().isEmpty(), "stale nodes must not be upload targets");
        assertEquals(Set.of(NODE_URL), service.getStaleNodeUrls());
        assertTrue(service.getFailedNodeUrls().isEmpty(), "stale nodes keep their replica records");
    }

    @Test
    void staleNodeIsNotFailedBeforeTheDeadTimeout() {
        service.recordHeartbeat(NODE_ID, NODE_URL);
        advanceAndCheck(STALE_MS + 1_000);
        advanceAndCheck(DEAD_MS - STALE_MS - 2_000);

        assertEquals(Set.of(NODE_URL), service.getStaleNodeUrls());
        assertTrue(service.getFailedNodeUrls().isEmpty());
    }

    @Test
    void staleNodeBecomesFailedAfterTheDeadTimeout() {
        service.recordHeartbeat(NODE_ID, NODE_URL);
        advanceAndCheck(STALE_MS + 1_000);
        advanceAndCheck(DEAD_MS);

        assertEquals(Set.of(NODE_URL), service.getFailedNodeUrls());
        assertTrue(service.getStaleNodeUrls().isEmpty());
        assertTrue(service.getActiveNodeUrls().isEmpty());
    }

    @Test
    void staleNodeThatReturnsIsActiveAgainWithoutReconciliation() {
        service.recordHeartbeat(NODE_ID, NODE_URL);
        service.drainRejoinedNodeUrls(); // clear the first-seen entry
        advanceAndCheck(STALE_MS + 1_000);

        service.recordHeartbeat(NODE_ID, NODE_URL);

        assertEquals(Set.of(NODE_URL), service.getActiveNodeUrls());
        assertTrue(service.getStaleNodeUrls().isEmpty());
        assertTrue(service.drainRejoinedNodeUrls().isEmpty(), "its replicas were never dropped, nothing to reconcile");
    }

    @Test
    void failedNodeThatReturnsIsQueuedForReconciliation() {
        service.recordHeartbeat(NODE_ID, NODE_URL);
        service.drainRejoinedNodeUrls();
        advanceAndCheck(DEAD_MS + 1_000);
        assertEquals(Set.of(NODE_URL), service.getFailedNodeUrls());

        service.recordHeartbeat(NODE_ID, NODE_URL);

        assertEquals(Set.of(NODE_URL), service.getActiveNodeUrls());
        assertTrue(service.getFailedNodeUrls().isEmpty());
        assertEquals(Set.of(NODE_URL), service.drainRejoinedNodeUrls());
        assertTrue(service.drainRejoinedNodeUrls().isEmpty(), "draining clears the queue");
    }

    @Test
    void nodeSeenForTheFirstTimeIsQueuedForReconciliation() {
        service.recordHeartbeat(NODE_ID, NODE_URL);

        assertEquals(Set.of(NODE_URL), service.drainRejoinedNodeUrls());
    }

    @Test
    void rejectsTimeoutsWhereDeadIsNotLargerThanStale() {
        assertThrows(IllegalArgumentException.class,
                () -> new HeartbeatService(mock(EventLogService.class), 15_000, 15_000, now::get));
        assertThrows(IllegalArgumentException.class,
                () -> new HeartbeatService(mock(EventLogService.class), 0, 60_000, now::get));
    }
}

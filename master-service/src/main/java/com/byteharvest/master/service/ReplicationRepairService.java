package com.byteharvest.master.service;

import com.byteharvest.master.model.ReconcileResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.util.Set;

@Service
public class ReplicationRepairService {

    private static final Logger logger = LoggerFactory.getLogger(ReplicationRepairService.class);

    private final HeartbeatService heartbeatService;
    private final ChunkRepairService chunkRepairService;
    private final EventLogService eventLogService;

    public ReplicationRepairService(HeartbeatService heartbeatService, ChunkRepairService chunkRepairService, EventLogService eventLogService) {
        this.heartbeatService = heartbeatService;
        this.chunkRepairService = chunkRepairService;
        this.eventLogService = eventLogService;
    }

    @Scheduled(fixedRate = 10000, initialDelay = 5000)
    public void repairReplication() {
        // Reconcile returning nodes first, so a valid chunk they still hold is adopted
        // before the repair below copies it somewhere else.
        reconcileReturningNodes();

        Set<String> failedNodeUrls = heartbeatService.getFailedNodeUrls();
        int removedReplicaReferences = chunkRepairService.removeFailedNodeReferences(failedNodeUrls);

        Set<String> activeNodeUrls = heartbeatService.getActiveNodeUrls();
        int corruptedRepaired = chunkRepairService.verifyAndRepairCorruptions(activeNodeUrls);

        int repairedReplicas = chunkRepairService.repairUnderReplicatedChunks(activeNodeUrls);

        if (removedReplicaReferences > 0 || repairedReplicas > 0 || corruptedRepaired > 0) {
            logger.info(
                    "Replication repair: removed {} failed replica refs, repaired {} corruptions, added {} replicas",
                    removedReplicaReferences,
                    corruptedRepaired,
                    repairedReplicas
            );
            eventLogService.info(
                    "REPAIR",
                    "Replication repair: removed " + removedReplicaReferences + " failed refs, repaired " + corruptedRepaired + " corruptions, added " + repairedReplicas + " replicas"
            );
        }
    }

    private void reconcileReturningNodes() {
        Set<String> pending = heartbeatService.drainRejoinedNodeUrls();
        if (pending.isEmpty()) {
            return;
        }

        Set<String> activeNodeUrls = heartbeatService.getActiveNodeUrls();

        for (String nodeUrl : pending) {
            if (!activeNodeUrls.contains(nodeUrl)) {
                // The node went away again before we got to it; try once it is back.
                heartbeatService.requeueRejoinedNode(nodeUrl);
                continue;
            }

            try {
                ReconcileResult result = chunkRepairService.reconcileNode(nodeUrl, activeNodeUrls);
                if (result.hasChanges()) {
                    eventLogService.info("REPAIR", "Reconciled " + nodeUrl + ": adopted " + result.adopted()
                            + " chunks, removed " + result.surplusRemoved() + " surplus and " + result.corruptRemoved()
                            + " corrupt copies, " + result.unknownKept() + " unknown chunks left in place, "
                            + result.skipped() + " skipped");
                }
            } catch (HttpClientErrorException.NotFound e) {
                eventLogService.warn("REPAIR", "Cannot reconcile " + nodeUrl
                        + ": it does not support GET /chunks. Rebuild the storage node.");
            } catch (Exception e) {
                heartbeatService.requeueRejoinedNode(nodeUrl);
                eventLogService.warn("REPAIR", "Could not reconcile " + nodeUrl + " yet, will retry: " + e.getMessage());
            }
        }
    }
}

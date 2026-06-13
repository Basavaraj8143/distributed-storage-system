package com.byteharvest.master.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class ReplicationRepairService {

    private static final Logger logger = LoggerFactory.getLogger(ReplicationRepairService.class);

    private final HeartbeatService heartbeatService;
    private final ChunkService chunkService;
    private final EventLogService eventLogService;

    public ReplicationRepairService(HeartbeatService heartbeatService, ChunkService chunkService, EventLogService eventLogService) {
        this.heartbeatService = heartbeatService;
        this.chunkService = chunkService;
        this.eventLogService = eventLogService;
    }

    @Scheduled(fixedRate = 10000, initialDelay = 5000)
    public void repairReplication() {
        Set<String> failedNodeUrls = heartbeatService.getFailedNodeUrls();
        int removedReplicaReferences = chunkService.removeFailedNodeReferences(failedNodeUrls);

        Set<String> activeNodeUrls = heartbeatService.getActiveNodeUrls();
        int corruptedRepaired = chunkService.verifyAndRepairCorruptions(activeNodeUrls);

        int repairedReplicas = chunkService.repairUnderReplicatedChunks(activeNodeUrls);

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
}

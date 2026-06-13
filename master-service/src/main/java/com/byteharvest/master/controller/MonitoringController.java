package com.byteharvest.master.controller;

import com.byteharvest.master.service.HeartbeatService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
public class MonitoringController {

    private final HeartbeatService heartbeatService;

    public MonitoringController(HeartbeatService heartbeatService) {
        this.heartbeatService = heartbeatService;
    }

    @GetMapping("/nodes/status")
    public List<Map<String, Object>> getNodeStatus() {
        List<Map<String, Object>> response = new ArrayList<>();
        Set<String> active = heartbeatService.getActiveNodes();
        Set<String> failed = heartbeatService.getFailedNodes();

        for (String nodeId : heartbeatService.getKnownNodeIds()) {
            Map<String, Object> node = new HashMap<>();
            node.put("nodeId", nodeId);
            node.put("nodeUrl", heartbeatService.getNodeUrl(nodeId));
            node.put("lastHeartbeat", heartbeatService.getLastSeenMillis(nodeId));

            if (active.contains(nodeId)) {
                node.put("status", "ACTIVE");
            } else if (failed.contains(nodeId)) {
                node.put("status", "FAILED");
            } else {
                node.put("status", "UNKNOWN");
            }
            response.add(node);
        }

        return response;
    }

    @GetMapping("/system/health")
    public Map<String, Object> getSystemHealth() {
        int activeNodes = heartbeatService.getActiveNodes().size();
        int failedNodes = heartbeatService.getFailedNodes().size();

        String status;
        if (activeNodes == 0 && failedNodes == 0) {
            status = "UNKNOWN";
        } else if (failedNodes > 0) {
            status = "DEGRADED";
        } else {
            status = "HEALTHY";
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", status);
        response.put("activeNodes", activeNodes);
        response.put("failedNodes", failedNodes);
        response.put("activeNodeIds", heartbeatService.getActiveNodes());
        response.put("failedNodeIds", heartbeatService.getFailedNodes());
        return response;
    }
}


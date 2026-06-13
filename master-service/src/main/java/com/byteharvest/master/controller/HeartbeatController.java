package com.byteharvest.master.controller;

import com.byteharvest.master.model.HeartbeatRequest;
import com.byteharvest.master.service.HeartbeatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HeartbeatController {

    private static final Logger logger = LoggerFactory.getLogger(HeartbeatController.class);

    private final HeartbeatService heartbeatService;

    public HeartbeatController(HeartbeatService heartbeatService) {
        this.heartbeatService = heartbeatService;
    }

    @PostMapping("/heartbeat")
    public ResponseEntity<String> receiveHeartbeat(@RequestBody HeartbeatRequest request) {
        if (request == null || request.getNodeId() == null || request.getNodeId().isBlank()) {
            return ResponseEntity.badRequest().body("nodeId is required");
        }
        if (request.getNodeUrl() == null || request.getNodeUrl().isBlank()) {
            return ResponseEntity.badRequest().body("nodeUrl is required");
        }

        heartbeatService.recordHeartbeat(request.getNodeId(), request.getNodeUrl());
        logger.info("Heartbeat received from {} ({})", request.getNodeId(), request.getNodeUrl());
        return ResponseEntity.ok("Heartbeat received");
    }
}

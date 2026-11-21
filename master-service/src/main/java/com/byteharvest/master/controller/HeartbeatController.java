package com.byteharvest.master.controller;

import com.byteharvest.master.model.HeartbeatRequest;
import com.byteharvest.master.service.HeartbeatService;
import com.byteharvest.master.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Heartbeat", description = "Called by storage nodes, not by users")
public class HeartbeatController {

    private static final Logger logger = LoggerFactory.getLogger(HeartbeatController.class);

    private final HeartbeatService heartbeatService;

    public HeartbeatController(HeartbeatService heartbeatService) {
        this.heartbeatService = heartbeatService;
    }

    @Operation(summary = "Receive a node heartbeat",
            description = "Registers or refreshes a storage node. Needs the X-Node-Token header; the user login does not work here.",
            security = @SecurityRequirement(name = OpenApiConfig.NODE_TOKEN))
    @ApiResponse(responseCode = "200", description = "Heartbeat recorded")
    @ApiResponse(responseCode = "400", description = "nodeId or nodeUrl missing")
    @ApiResponse(responseCode = "401", description = "Missing or wrong X-Node-Token")
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

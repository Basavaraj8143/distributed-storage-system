package com.byteharvest.storage_node.controller;

import com.byteharvest.storage_node.service.HeartbeatSenderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/simulate")
public class SimulationController {

    private final HeartbeatSenderService heartbeatSenderService;

    public SimulationController(HeartbeatSenderService heartbeatSenderService) {
        this.heartbeatSenderService = heartbeatSenderService;
    }

    @PostMapping("/offline")
    public ResponseEntity<String> simulateOffline() {
        heartbeatSenderService.setOffline(true);
        return ResponseEntity.ok("Node is now simulated offline");
    }

    @PostMapping("/online")
    public ResponseEntity<String> simulateOnline() {
        heartbeatSenderService.setOffline(false);
        return ResponseEntity.ok("Node is now simulated online");
    }
}

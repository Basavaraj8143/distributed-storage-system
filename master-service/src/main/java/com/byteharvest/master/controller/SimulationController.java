package com.byteharvest.master.controller;

import com.byteharvest.master.service.HeartbeatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/api/simulation")
public class SimulationController {

    private final HeartbeatService heartbeatService;
    private final RestTemplate restTemplate;

    public SimulationController(HeartbeatService heartbeatService, RestTemplate restTemplate) {
        this.heartbeatService = heartbeatService;
        this.restTemplate = restTemplate;
    }

    @PostMapping("/nodes/{nodeId}/offline")
    public ResponseEntity<String> simulateOffline(@PathVariable String nodeId) {
        String nodeUrl = heartbeatService.getNodeUrl(nodeId);
        if (nodeUrl == null) {
            return ResponseEntity.status(404).body("Node not found");
        }
        
        try {
            restTemplate.postForEntity(nodeUrl + "/simulate/offline", null, String.class);
            return ResponseEntity.ok("Simulating node offline");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error talking to node");
        }
    }

    @PostMapping("/nodes/{nodeId}/online")
    public ResponseEntity<String> simulateOnline(@PathVariable String nodeId) {
        String nodeUrl = heartbeatService.getNodeUrl(nodeId);
        if (nodeUrl == null) {
            // Node url might be unknown if it never reported in.
            // But if it's failed, heartbeat service still remembers its last known URL!
            return ResponseEntity.status(404).body("Node not found");
        }

        try {
            restTemplate.postForEntity(nodeUrl + "/simulate/online", null, String.class);
            return ResponseEntity.ok("Simulating node online");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error talking to node");
        }
    }
}

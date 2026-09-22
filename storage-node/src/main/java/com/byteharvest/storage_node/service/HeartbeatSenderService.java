package com.byteharvest.storage_node.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class HeartbeatSenderService {

    private static final Logger logger = LoggerFactory.getLogger(HeartbeatSenderService.class);

    private final RestTemplate restTemplate;
    private final String masterHeartbeatUrl;
    private final String serverPort;

    public HeartbeatSenderService(
            RestTemplate restTemplate,
            @Value("${heartbeat.master-url:http://localhost:8080/heartbeat}") String masterHeartbeatUrl,
            @Value("${server.port:5001}") String serverPort) {
        this.restTemplate = restTemplate;
        this.masterHeartbeatUrl = masterHeartbeatUrl;
        this.serverPort = serverPort;
    }

    private volatile boolean isOffline = false;

    public void setOffline(boolean offline) {
        this.isOffline = offline;
    }

    public boolean isOffline() {
        return isOffline;
    }

    @Scheduled(fixedRate = 5000)
    public void sendHeartbeat() {
        if (isOffline) {
            logger.info("Node is simulating offline mode. Skipping heartbeat.");
            return;
        }

        String nodeId = resolveNodeId();
        String nodeUrl = "http://localhost:" + serverPort;

        try {
            restTemplate.postForEntity(
                    masterHeartbeatUrl,
                    Map.of("nodeId", nodeId, "nodeUrl", nodeUrl),
                    String.class
            );
            logger.info("Heartbeat sent from {} ({})", nodeId, nodeUrl);
        } catch (Exception exception) {
            logger.warn("Failed to send heartbeat from {}: {}", nodeId, exception.getMessage());
        }
    }

    private String resolveNodeId() {
        String port = serverPort;

        if ("5001".equals(port)) {
            return "node-1";
        }
        if ("5002".equals(port)) {
            return "node-2";
        }
        if ("5003".equals(port)) {
            return "node-3";
        }
        return "node-" + port;
    }
}

package com.byteharvest.storage_node.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.*;

@RestController
public class StorageController {

    @Value("${storage.base-dir:}")
    private String storageBaseDir;

    @Value("${server.port:5001}")
    private String serverPort;

    private final com.byteharvest.storage_node.service.HeartbeatSenderService heartbeatSenderService;

    public StorageController(com.byteharvest.storage_node.service.HeartbeatSenderService heartbeatSenderService) {
        this.heartbeatSenderService = heartbeatSenderService;
    }

    private Path getChunkPath(String chunkId) {
        String port = serverPort;
        String fileName = "chunk_" + chunkId;

        if (storageBaseDir == null || storageBaseDir.isBlank()) {
            return Paths.get("storage_" + port, fileName);
        }

        return Paths.get(storageBaseDir, "storage_" + port, fileName);
    }

    @PostMapping("/storeChunk")
    public ResponseEntity<String> storeChunk(
            @RequestParam("chunkId") String chunkId,
            @RequestParam("file") MultipartFile file) {

        if (heartbeatSenderService.isOffline()) {
            return ResponseEntity.status(503).body("Node is offline");
        }

        try {
            Path path = getChunkPath(chunkId);

            // create folder if not exists
            Files.createDirectories(path.getParent());

            // save chunk
            Files.write(path, file.getBytes());

            return ResponseEntity.ok("Stored");

        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error storing chunk");
        }
    }

    @GetMapping("/getChunk/{chunkId}")
    public ResponseEntity<byte[]> getChunk(@PathVariable String chunkId) {

        if (heartbeatSenderService.isOffline()) {
            return ResponseEntity.status(503).build();
        }

        try {
            Path path = getChunkPath(chunkId);

            byte[] data = Files.readAllBytes(path);

            return ResponseEntity.ok(data);

        } catch (Exception e) {
            return ResponseEntity.status(404).build();
        }
    }
}

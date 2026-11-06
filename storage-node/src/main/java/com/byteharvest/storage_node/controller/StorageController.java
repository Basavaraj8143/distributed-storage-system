package com.byteharvest.storage_node.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.*;
import java.util.List;
import java.util.stream.Stream;

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

    private Path getChunkDir() {
        if (storageBaseDir == null || storageBaseDir.isBlank()) {
            return Paths.get("storage_" + serverPort);
        }

        return Paths.get(storageBaseDir, "storage_" + serverPort);
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

    /**
     * Lists the ids of every chunk stored on this node. The master calls this when a node rejoins,
     * to compare what the node holds against the metadata.
     */
    @GetMapping("/chunks")
    public ResponseEntity<List<String>> listChunks() {

        if (heartbeatSenderService.isOffline()) {
            return ResponseEntity.status(503).build();
        }

        Path dir = getChunkDir();
        if (!Files.isDirectory(dir)) {
            return ResponseEntity.ok(List.of());
        }

        try (Stream<Path> files = Files.list(dir)) {
            List<String> chunkIds = files
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("chunk_"))
                    .map(name -> name.substring("chunk_".length()))
                    .sorted()
                    .toList();
            return ResponseEntity.ok(chunkIds);

        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    @DeleteMapping("/deleteChunk/{chunkId}")
    public ResponseEntity<String> deleteChunk(@PathVariable String chunkId) {

        if (heartbeatSenderService.isOffline()) {
            return ResponseEntity.status(503).body("Node is offline");
        }

        try {
            Files.deleteIfExists(getChunkPath(chunkId));
            return ResponseEntity.ok("Deleted");

        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error deleting chunk");
        }
    }
}

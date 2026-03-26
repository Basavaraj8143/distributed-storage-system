package com.byteharvest.storage_node.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.*;

@RestController
public class StorageController {

    @PostMapping("/storeChunk")
    public ResponseEntity<String> storeChunk(
            @RequestParam("chunkId") String chunkId,
            @RequestParam("file") MultipartFile file) {

        try {
            Path path = Paths.get("storage/chunk_" + chunkId);

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

        try {
            Path path = Paths.get("storage/chunk_" + chunkId);

            byte[] data = Files.readAllBytes(path);

            return ResponseEntity.ok(data);

        } catch (Exception e) {
            return ResponseEntity.status(404).build();
        }
    }
}
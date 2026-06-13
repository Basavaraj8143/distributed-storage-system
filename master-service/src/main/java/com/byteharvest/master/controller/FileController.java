package com.byteharvest.master.controller;

import com.byteharvest.master.service.ChunkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class FileController {

    @Autowired
    private ChunkService chunkService;

    @PostMapping("/upload")
    public ResponseEntity<String> upload(@RequestParam("file") MultipartFile file) {
        String fileId = chunkService.handleUpload(file);
        return ResponseEntity.ok(fileId);
    }

    @GetMapping("/download/{fileId}")
    public ResponseEntity<byte[]> download(@PathVariable String fileId) {
        byte[] data = chunkService.download(fileId);

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=output.pdf")
                .header("Content-Type", "application/octet-stream")
                .body(data);
    }
}

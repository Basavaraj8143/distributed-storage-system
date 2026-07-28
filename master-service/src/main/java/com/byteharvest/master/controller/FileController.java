package com.byteharvest.master.controller;

import com.byteharvest.master.service.ChunkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

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
        String filename = chunkService.getOriginalFileName(fileId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(filename, fileId))
                .header("Content-Type", "application/octet-stream")
                .body(data);
    }

    private String contentDisposition(String originalFilename, String fileId) {
        String filename = originalFilename == null || originalFilename.isBlank()
                ? "download-" + fileId
                : originalFilename;
        String safeFilename = filename.replaceAll("[\\r\\n\";]", "");
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + safeFilename + "\"; filename*=UTF-8''" + encodedFilename;
    }
}

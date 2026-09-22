package com.byteharvest.master.controller;

import com.byteharvest.master.service.ChunkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
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
        MediaType mediaType = resolveContentType(filename, data);

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(filename, fileId, mediaType))
                .body(data);
    }

    private MediaType resolveContentType(String filename, byte[] data) {
        if (filename != null && !filename.isBlank()) {
            return MediaTypeFactory.getMediaType(filename)
                    .orElse(MediaType.APPLICATION_OCTET_STREAM);
        }

        if (data != null
                && data.length >= 4
                && data[0] == '%'
                && data[1] == 'P'
                && data[2] == 'D'
                && data[3] == 'F') {
            return MediaType.APPLICATION_PDF;
        }

        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private String contentDisposition(String originalFilename, String fileId, MediaType mediaType) {
        String filename = originalFilename == null || originalFilename.isBlank()
                ? fallbackFilename(fileId, mediaType)
                : originalFilename;
        String safeFilename = filename.replaceAll("[\\r\\n\";]", "");
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + safeFilename + "\"; filename*=UTF-8''" + encodedFilename;
    }

    private String fallbackFilename(String fileId, MediaType mediaType) {
        if (MediaType.APPLICATION_PDF.equals(mediaType)) {
            return "download-" + fileId + ".pdf";
        }

        return "download-" + fileId;
    }
}

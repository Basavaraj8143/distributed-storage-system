package com.byteharvest.master.controller;

import com.byteharvest.master.exception.InvalidRequestException;
import com.byteharvest.master.model.FileDetails;
import com.byteharvest.master.model.FileSummary;
import com.byteharvest.master.service.ChunkService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

@RestController
public class FileController {

    // File ids are generated with UUID.randomUUID(), so anything else is a bad request.
    private static final Pattern FILE_ID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final int MAX_FILENAME_LENGTH = 255;
    private static final String DEFAULT_FILENAME = "unnamed";

    private final ChunkService chunkService;

    public FileController(ChunkService chunkService) {
        this.chunkService = chunkService;
    }

    @PostMapping("/upload")
    public ResponseEntity<String> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new InvalidRequestException("Uploaded file is empty.");
        }

        String filename = sanitizeFilename(file.getOriginalFilename());
        String fileId = chunkService.handleUpload(file, filename);
        return ResponseEntity.ok(fileId);
    }

    @GetMapping("/download/{fileId}")
    public ResponseEntity<byte[]> download(@PathVariable String fileId) {
        requireValidFileId(fileId);

        byte[] data = chunkService.download(fileId);
        String filename = chunkService.getOriginalFileName(fileId);
        MediaType mediaType = resolveContentType(filename, data);

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(filename, fileId, mediaType))
                .body(data);
    }

    @GetMapping("/files")
    public List<FileSummary> listFiles() {
        return chunkService.listFiles();
    }

    @GetMapping("/files/{fileId}")
    public FileDetails getFile(@PathVariable String fileId) {
        requireValidFileId(fileId);
        return chunkService.getFileDetails(fileId);
    }

    @DeleteMapping("/files/{fileId}")
    public ResponseEntity<Void> deleteFile(@PathVariable String fileId) {
        requireValidFileId(fileId);
        chunkService.deleteFile(fileId);
        return ResponseEntity.noContent().build();
    }

    private void requireValidFileId(String fileId) {
        if (fileId == null || !FILE_ID_PATTERN.matcher(fileId).matches()) {
            throw new InvalidRequestException("Invalid file id format.");
        }
    }

    /**
     * Keeps only the base name (some browsers send a full path), removes control characters,
     * and falls back to a default when nothing usable is left.
     */
    private String sanitizeFilename(String originalFilename) {
        if (originalFilename == null) {
            return DEFAULT_FILENAME;
        }

        String name = originalFilename.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("\\p{Cntrl}", "").trim();

        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            return DEFAULT_FILENAME;
        }
        if (name.length() > MAX_FILENAME_LENGTH) {
            throw new InvalidRequestException("Filename is too long (maximum " + MAX_FILENAME_LENGTH + " characters).");
        }
        return name;
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

package com.byteharvest.master.controller;

import com.byteharvest.master.exception.InvalidRequestException;
import com.byteharvest.master.model.FileDetails;
import com.byteharvest.master.model.FileSummary;
import com.byteharvest.master.service.DownloadService;
import com.byteharvest.master.service.FileManagementService;
import com.byteharvest.master.service.UploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Files", description = "Upload, download, list and delete files")
public class FileController {

    // File ids are generated with UUID.randomUUID(), so anything else is a bad request.
    private static final Pattern FILE_ID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final int MAX_FILENAME_LENGTH = 255;
    private static final String DEFAULT_FILENAME = "unnamed";

    private final UploadService uploadService;
    private final DownloadService downloadService;
    private final FileManagementService fileManagementService;

    public FileController(UploadService uploadService,
                          DownloadService downloadService,
                          FileManagementService fileManagementService) {
        this.uploadService = uploadService;
        this.downloadService = downloadService;
        this.fileManagementService = fileManagementService;
    }

    @Operation(summary = "Upload a file",
            description = "Splits the file into chunks and stores each chunk on several ACTIVE nodes. Returns the new fileId.")
    @ApiResponse(responseCode = "200", description = "File stored; body is the fileId (UUID)")
    @ApiResponse(responseCode = "400", description = "Empty file, missing file part or filename too long")
    @ApiResponse(responseCode = "413", description = "File larger than 10 MB")
    @ApiResponse(responseCode = "503", description = "Not enough ACTIVE nodes to store the replicas")
    @PostMapping("/upload")
    public ResponseEntity<String> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new InvalidRequestException("Uploaded file is empty.");
        }

        String filename = sanitizeFilename(file.getOriginalFilename());
        String fileId = uploadService.handleUpload(file, filename);
        return ResponseEntity.ok(fileId);
    }

    @Operation(summary = "Download a file",
            description = "Reassembles the file from its chunks, verifying each checksum and falling back to other replicas.")
    @ApiResponse(responseCode = "200", description = "The file bytes")
    @ApiResponse(responseCode = "400", description = "fileId is not a valid UUID")
    @ApiResponse(responseCode = "404", description = "Unknown fileId")
    @ApiResponse(responseCode = "503", description = "A chunk has no valid replica right now")
    @GetMapping("/download/{fileId}")
    public ResponseEntity<byte[]> download(
            @Parameter(description = "Id returned by upload", example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable String fileId) {
        requireValidFileId(fileId);

        byte[] data = downloadService.download(fileId);
        String filename = fileManagementService.getOriginalFileName(fileId);
        MediaType mediaType = resolveContentType(filename, data);

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(filename, fileId, mediaType))
                .body(data);
    }

    @Operation(summary = "List files",
            description = "Every file with its chunk count and its lowest replica count (below targetReplicas means under-replicated).")
    @GetMapping("/files")
    public List<FileSummary> listFiles() {
        return fileManagementService.listFiles();
    }

    @Operation(summary = "File details", description = "Chunks of one file with checksums and the nodes holding each replica.")
    @ApiResponse(responseCode = "400", description = "fileId is not a valid UUID")
    @ApiResponse(responseCode = "404", description = "Unknown fileId")
    @GetMapping("/files/{fileId}")
    public FileDetails getFile(
            @Parameter(description = "Id returned by upload", example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable String fileId) {
        requireValidFileId(fileId);
        return fileManagementService.getFileDetails(fileId);
    }

    @Operation(summary = "Delete a file",
            description = "Removes the metadata first, then asks the nodes to delete the chunks (best effort).")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "400", description = "fileId is not a valid UUID")
    @ApiResponse(responseCode = "404", description = "Unknown fileId")
    @DeleteMapping("/files/{fileId}")
    public ResponseEntity<Void> deleteFile(
            @Parameter(description = "Id returned by upload", example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable String fileId) {
        requireValidFileId(fileId);
        fileManagementService.deleteFile(fileId);
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

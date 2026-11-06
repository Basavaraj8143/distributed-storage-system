package com.byteharvest.master;

import com.byteharvest.master.controller.FileController;
import com.byteharvest.master.exception.ChunkUnavailableException;
import com.byteharvest.master.exception.StoredFileNotFoundException;
import com.byteharvest.master.model.ChunkInfo;
import com.byteharvest.master.model.FileDetails;
import com.byteharvest.master.model.FileSummary;
import com.byteharvest.master.service.ChunkService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FileController.class)
class FileControllerTest {

    private static final String FILE_ID = "123e4567-e89b-12d3-a456-426614174000";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChunkService chunkService;

    // ---- upload ----

    @Test
    void uploadRejectsEmptyFile() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("file", "a.txt", "text/plain", new byte[0]);

        mockMvc.perform(multipart("/upload").file(empty))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Uploaded file is empty."));

        verify(chunkService, never()).handleUpload(any(MultipartFile.class), any());
    }

    @Test
    void uploadWithoutFilePartIsBadRequest() throws Exception {
        mockMvc.perform(multipart("/upload"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void uploadKeepsOnlyTheBaseFilename() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "C:\\docs\\..\\report.pdf", "application/pdf", "data".getBytes());
        when(chunkService.handleUpload(any(MultipartFile.class), eq("report.pdf"))).thenReturn(FILE_ID);

        mockMvc.perform(multipart("/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(content().string(FILE_ID));
    }

    @Test
    void uploadRejectsTooLongFilename() throws Exception {
        String longName = "a".repeat(300) + ".txt";
        MockMultipartFile file = new MockMultipartFile("file", longName, "text/plain", "data".getBytes());

        mockMvc.perform(multipart("/upload").file(file))
                .andExpect(status().isBadRequest());
    }

    // ---- download ----

    @Test
    void downloadWithMalformedIdIsBadRequest() throws Exception {
        mockMvc.perform(get("/download/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid file id format."));

        verify(chunkService, never()).download(any());
    }

    @Test
    void downloadUnknownFileIsNotFound() throws Exception {
        when(chunkService.download(FILE_ID)).thenThrow(new StoredFileNotFoundException(FILE_ID));

        mockMvc.perform(get("/download/" + FILE_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void downloadWithNoHealthyReplicaIsServiceUnavailable() throws Exception {
        when(chunkService.download(FILE_ID)).thenThrow(new ChunkUnavailableException("All replicas failed"));

        mockMvc.perform(get("/download/" + FILE_ID))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void unexpectedErrorsDoNotLeakDetails() throws Exception {
        when(chunkService.download(FILE_ID)).thenThrow(new RuntimeException("jdbc:sqlite:secret-path failed"));

        mockMvc.perform(get("/download/" + FILE_ID))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Internal server error."));
    }

    // ---- list / details / delete ----

    @Test
    void listFilesReturnsSummaries() throws Exception {
        when(chunkService.listFiles()).thenReturn(List.of(new FileSummary(FILE_ID, "a.txt", 3, 2, 2)));

        mockMvc.perform(get("/files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fileId").value(FILE_ID))
                .andExpect(jsonPath("$[0].filename").value("a.txt"))
                .andExpect(jsonPath("$[0].chunkCount").value(3))
                .andExpect(jsonPath("$[0].minReplicas").value(2))
                .andExpect(jsonPath("$[0].targetReplicas").value(2));
    }

    @Test
    void getFileReturnsChunkDetails() throws Exception {
        ChunkInfo chunk = new ChunkInfo("chunk-1", 0, "abc123", List.of("http://localhost:5001", "http://localhost:5002"));
        when(chunkService.getFileDetails(FILE_ID)).thenReturn(new FileDetails(FILE_ID, "a.txt", 1, 2, List.of(chunk)));

        mockMvc.perform(get("/files/" + FILE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetReplicas").value(2))
                .andExpect(jsonPath("$.chunks[0].chunkId").value("chunk-1"))
                .andExpect(jsonPath("$.chunks[0].nodeUrls[1]").value("http://localhost:5002"));
    }

    @Test
    void getUnknownFileIsNotFound() throws Exception {
        when(chunkService.getFileDetails(FILE_ID)).thenThrow(new StoredFileNotFoundException(FILE_ID));

        mockMvc.perform(get("/files/" + FILE_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteFileReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/files/" + FILE_ID))
                .andExpect(status().isNoContent());

        verify(chunkService).deleteFile(FILE_ID);
    }

    @Test
    void deleteUnknownFileIsNotFound() throws Exception {
        doThrow(new StoredFileNotFoundException(FILE_ID)).when(chunkService).deleteFile(FILE_ID);

        mockMvc.perform(delete("/files/" + FILE_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteWithMalformedIdIsBadRequest() throws Exception {
        mockMvc.perform(delete("/files/not-a-uuid"))
                .andExpect(status().isBadRequest());

        verify(chunkService, never()).deleteFile(any());
    }
}

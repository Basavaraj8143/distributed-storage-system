package com.byteharvest.master.model;

import java.util.List;

public class FileMetadata {
    private String fileId;
    private List<ChunkMetadata> chunks;

    public FileMetadata(String fileId, List<ChunkMetadata> chunks) {
        this.fileId = fileId;
        this.chunks = chunks;
    }

    public String getFileId() {
        return fileId;
    }

    public List<ChunkMetadata> getChunks() {
        return chunks;
    }
}

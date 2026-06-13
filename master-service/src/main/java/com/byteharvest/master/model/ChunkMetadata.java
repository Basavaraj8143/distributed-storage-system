package com.byteharvest.master.model;

import java.util.List;

public class ChunkMetadata {

    private String chunkId;
    private int chunkIndex;
    private List<String> nodeUrls;
    private String checksum;

    public ChunkMetadata(String chunkId, int chunkIndex, List<String> nodeUrls, String checksum) {
        this.chunkId = chunkId;
        this.chunkIndex = chunkIndex;
        this.nodeUrls = nodeUrls;
        this.checksum = checksum;
    }

    public String getChunkId() {
        return chunkId;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public List<String> getNodeUrls() {
        return nodeUrls;
    }

    public void setNodeUrls(List<String> nodeUrls) {
        this.nodeUrls = nodeUrls;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }
}

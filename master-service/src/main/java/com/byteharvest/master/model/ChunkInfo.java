package com.byteharvest.master.model;

import java.util.List;

public record ChunkInfo(String chunkId, int chunkIndex, String checksum, List<String> nodeUrls) {
}

package com.byteharvest.master.model;

import java.util.List;

public record FileDetails(String fileId, String filename, int chunkCount, int targetReplicas, List<ChunkInfo> chunks) {
}

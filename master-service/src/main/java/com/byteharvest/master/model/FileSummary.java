package com.byteharvest.master.model;

/**
 * One row of the file list. minReplicas is the lowest live replica count across the file's chunks;
 * a value below targetReplicas means the file is under-replicated.
 */
public record FileSummary(String fileId, String filename, int chunkCount, int minReplicas, int targetReplicas) {
}

package com.byteharvest.master.model;

/**
 * One chunk file as reported by a storage node's GET /chunks.
 */
public record NodeChunk(String chunkId, long size, long lastModified) {
}

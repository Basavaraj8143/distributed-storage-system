package com.byteharvest.master.exception;

/**
 * Thrown when no healthy replica of a chunk can be read. Mapped to HTTP 503.
 */
public class ChunkUnavailableException extends RuntimeException {

    public ChunkUnavailableException(String message) {
        super(message);
    }
}

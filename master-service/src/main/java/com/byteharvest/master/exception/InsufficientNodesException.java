package com.byteharvest.master.exception;

/**
 * Thrown when a chunk cannot be stored on enough live storage nodes. Mapped to HTTP 503.
 */
public class InsufficientNodesException extends RuntimeException {

    public InsufficientNodesException(String message) {
        super(message);
    }
}

package com.byteharvest.master.exception;

/**
 * Thrown when a file id has no metadata in the master. Mapped to HTTP 404.
 */
public class StoredFileNotFoundException extends RuntimeException {

    public StoredFileNotFoundException(String fileId) {
        super("File not found: " + fileId);
    }
}

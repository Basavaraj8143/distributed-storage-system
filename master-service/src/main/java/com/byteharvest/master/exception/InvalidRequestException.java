package com.byteharvest.master.exception;

/**
 * Thrown when a request is malformed (empty upload, bad file id, bad filename). Mapped to HTTP 400.
 */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}

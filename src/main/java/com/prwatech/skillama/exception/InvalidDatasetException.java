package com.prwatech.skillama.exception;

/** Thrown when an uploaded CSV fails validation — empty, too large, wrong type, corrupted, or malformed. */
public class InvalidDatasetException extends RuntimeException {
    public InvalidDatasetException(String message) {
        super(message);
    }
}

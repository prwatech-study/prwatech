package com.prwatech.skillama.exception;

/**
 * Raised when a Skillama Bearer token cannot be resolved to a learner/admin user id.
 */
public class SkillamaAuthException extends RuntimeException {

    public SkillamaAuthException(String message) {
        super(message);
    }
}

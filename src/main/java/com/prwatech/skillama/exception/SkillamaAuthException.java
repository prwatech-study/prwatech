package com.prwatech.skillama.exception;

/**
 * Raised when a Skillama Bearer token cannot be resolved to a learner/admin user id.
 */
public class SkillamaAuthException extends RuntimeException {

    /** Machine-readable cause; lets a caller like GET /session tell a client-side poller *why*. */
    private final String reason;

    public SkillamaAuthException(String message) {
        this(message, "AUTH_FAILED");
    }

    public SkillamaAuthException(String message, String reason) {
        super(message);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}

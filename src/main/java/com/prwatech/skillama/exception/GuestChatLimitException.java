package com.prwatech.skillama.exception;

/** Guest chat has hit its free-question cap (MAX_GUEST_QUESTIONS) — distinct from the
 * dollar-based {@link AiBudgetLimitException} that gates logged-in users. */
public class GuestChatLimitException extends IllegalStateException {
    public GuestChatLimitException(String message) {
        super(message);
    }
}

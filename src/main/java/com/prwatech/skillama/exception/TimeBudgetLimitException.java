package com.prwatech.skillama.exception;

import lombok.Getter;

/**
 * Thrown when a time-based (B2B seat) user has exhausted their allocated learning minutes.
 * Extends AiBudgetLimitException so every existing AI-controller catch site already maps it
 * to the 429 budget-limit response shape; the inherited USD fields carry MINUTES here —
 * read timeUsedMinutes/timeLimitMinutes for the correctly-named values.
 */
@Getter
public class TimeBudgetLimitException extends AiBudgetLimitException {
    /** Value of {@code limitType} for an exhausted B2B time seat. */
    public static final String LIMIT_TYPE_LEARNING_TIME = "LEARNING_TIME";

    private final double timeUsedMinutes;
    private final double timeLimitMinutes;

    public TimeBudgetLimitException(String message, double timeUsedMinutes, double timeLimitMinutes) {
        super(message, timeUsedMinutes, timeLimitMinutes);
        this.timeUsedMinutes = timeUsedMinutes;
        this.timeLimitMinutes = timeLimitMinutes;
    }

    @Override
    public String getLimitType() {
        return LIMIT_TYPE_LEARNING_TIME;
    }

    /** Adds correctly-named minute fields alongside the inherited USD-named ones. */
    @Override
    public java.util.Map<String, Object> toResponseBody() {
        java.util.Map<String, Object> body = super.toResponseBody();
        body.put("timeUsedMinutes", timeUsedMinutes);
        body.put("timeLimitMinutes", timeLimitMinutes);
        return body;
    }
}

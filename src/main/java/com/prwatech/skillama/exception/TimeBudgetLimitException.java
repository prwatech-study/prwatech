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
    private final double timeUsedMinutes;
    private final double timeLimitMinutes;

    public TimeBudgetLimitException(String message, double timeUsedMinutes, double timeLimitMinutes) {
        super(message, timeUsedMinutes, timeLimitMinutes);
        this.timeUsedMinutes = timeUsedMinutes;
        this.timeLimitMinutes = timeLimitMinutes;
    }
}

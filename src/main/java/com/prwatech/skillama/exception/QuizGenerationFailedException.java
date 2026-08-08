package com.prwatech.skillama.exception;

import lombok.Getter;

@Getter
public class QuizGenerationFailedException extends IllegalStateException {
    /** True once repeated generation failures make this module eligible for the "skip ahead" flow. */
    private final boolean skipEligible;

    public QuizGenerationFailedException(String message, boolean skipEligible) {
        super(message);
        this.skipEligible = skipEligible;
    }
}

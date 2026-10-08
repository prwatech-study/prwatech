package com.prwatech.skillama.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Interview rule failure. {@code code} is the stable value the LMS checks. */
@Getter
public class InterviewFlowException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public InterviewFlowException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }
}

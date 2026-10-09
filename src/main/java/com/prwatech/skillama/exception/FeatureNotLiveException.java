package com.prwatech.skillama.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Platform feature is not LIVE for this caller. {@code code} is stable for the LMS. */
@Getter
public class FeatureNotLiveException extends RuntimeException {
    public static final String CODE = "FEATURE_NOT_LIVE";

    private final String featureCode;
    private final HttpStatus status;

    public FeatureNotLiveException(String featureCode, String message) {
        super(message);
        this.featureCode = featureCode;
        this.status = HttpStatus.FORBIDDEN;
    }
}

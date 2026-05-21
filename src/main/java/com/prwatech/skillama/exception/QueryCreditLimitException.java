package com.prwatech.skillama.exception;

import lombok.Getter;

@Getter
public class QueryCreditLimitException extends IllegalStateException {
    private final int queryCreditsUsed;
    private final int queryCreditsLimit;

    public QueryCreditLimitException(String message, int queryCreditsUsed, int queryCreditsLimit) {
        super(message);
        this.queryCreditsUsed = queryCreditsUsed;
        this.queryCreditsLimit = queryCreditsLimit;
    }
}

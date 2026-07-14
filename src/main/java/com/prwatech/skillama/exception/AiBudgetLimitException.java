package com.prwatech.skillama.exception;

import lombok.Getter;

@Getter
public class AiBudgetLimitException extends IllegalStateException {
    private final double aiCostUsedUsd;
    private final double aiCostLimitUsd;

    public AiBudgetLimitException(String message, double aiCostUsedUsd, double aiCostLimitUsd) {
        super(message);
        this.aiCostUsedUsd = aiCostUsedUsd;
        this.aiCostLimitUsd = aiCostLimitUsd;
    }
}

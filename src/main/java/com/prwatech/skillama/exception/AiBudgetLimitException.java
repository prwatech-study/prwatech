package com.prwatech.skillama.exception;

import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
public class AiBudgetLimitException extends IllegalStateException {
    /** Value of {@code limitType} for a USD AI wallet (freemium, paid, or admin-granted). */
    public static final String LIMIT_TYPE_AI_WALLET = "AI_WALLET";

    private final double aiCostUsedUsd;
    private final double aiCostLimitUsd;

    public AiBudgetLimitException(String message, double aiCostUsedUsd, double aiCostLimitUsd) {
        super(message);
        this.aiCostUsedUsd = aiCostUsedUsd;
        this.aiCostLimitUsd = aiCostLimitUsd;
    }

    /**
     * Lets the client pick the right copy/CTA without parsing the message text.
     * Subclasses that cap a different resource (e.g. learning time) override this.
     */
    public String getLimitType() {
        return LIMIT_TYPE_AI_WALLET;
    }

    /**
     * The single 429 body shape every AI endpoint returns on exhaustion. Kept here
     * so controllers and {@link SkillamaExceptionHandler} cannot drift apart.
     */
    public Map<String, Object> toResponseBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "error");
        body.put("message", getMessage());
        body.put("aiBudgetLimitReached", true);
        body.put("limitType", getLimitType());
        body.put("aiCostUsedUsd", aiCostUsedUsd);
        body.put("aiCostLimitUsd", aiCostLimitUsd);
        return body;
    }
}

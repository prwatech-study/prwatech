package com.prwatech.skillama.exception;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiBudgetLimitExceptionTest {

    @Test
    void toResponseBody_includesCanonical429Shape() {
        AiBudgetLimitException ex =
                new AiBudgetLimitException("AI budget limit reached for this billing period", 5.0, 5.0);
        Map<String, Object> body = ex.toResponseBody();
        assertEquals("error", body.get("status"));
        assertEquals("AI budget limit reached for this billing period", body.get("message"));
        assertEquals(true, body.get("aiBudgetLimitReached"));
        assertEquals(AiBudgetLimitException.LIMIT_TYPE_AI_WALLET, body.get("limitType"));
        assertEquals(5.0, body.get("aiCostUsedUsd"));
        assertEquals(5.0, body.get("aiCostLimitUsd"));
    }

    @Test
    void timeSeat_usesLearningTimeLimitType() {
        TimeBudgetLimitException ex =
                new TimeBudgetLimitException("Learning time limit reached", 120.0, 120.0);
        Map<String, Object> body = ex.toResponseBody();
        assertEquals(TimeBudgetLimitException.LIMIT_TYPE_LEARNING_TIME, body.get("limitType"));
        assertEquals(120.0, body.get("timeUsedMinutes"));
        assertEquals(120.0, body.get("timeLimitMinutes"));
        assertTrue((Boolean) body.get("aiBudgetLimitReached"));
    }
}

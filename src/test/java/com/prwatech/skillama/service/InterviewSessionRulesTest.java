package com.prwatech.skillama.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterviewSessionRulesTest {

    private static final Instant START = Instant.parse("2026-10-08T10:00:00Z");

    @Test
    void slotEndIsScheduledStartPlusDuration() {
        assertEquals(Instant.parse("2026-10-08T10:20:00Z"), InterviewSessionRules.endsAt(START, 20));
    }

    @Test
    void remainingDoesNotGoNegativeAndCeilsPartialSeconds() {
        Instant ends = InterviewSessionRules.endsAt(START, 20);
        assertEquals(480, InterviewSessionRules.remainingSeconds(ends, Instant.parse("2026-10-08T10:12:00Z")));
        assertEquals(1, InterviewSessionRules.remainingSeconds(ends, ends.minusMillis(100)));
        assertEquals(0, InterviewSessionRules.remainingSeconds(ends, ends));
        assertEquals(0, InterviewSessionRules.remainingSeconds(ends, ends.plusSeconds(5)));
    }

    @Test
    void firstJoinWindowAllowsTenMinutesEarlyThroughGraceThenSlot() {
        Instant ends = InterviewSessionRules.endsAt(START, 20);
        assertEquals("TOO_EARLY",
                InterviewSessionRules.firstJoinDenial(START, 10, ends, START.minusSeconds(10 * 60 + 1)));
        assertNull(InterviewSessionRules.firstJoinDenial(START, 10, ends, START.minusSeconds(10 * 60)));
        assertNull(InterviewSessionRules.firstJoinDenial(START, 10, ends, START.minusSeconds(1)));
        assertNull(InterviewSessionRules.firstJoinDenial(START, 10, ends, START));
        assertNull(InterviewSessionRules.firstJoinDenial(START, 10, ends, START.plusSeconds(10 * 60)));
        assertEquals("JOIN_GRACE_EXPIRED",
                InterviewSessionRules.firstJoinDenial(START, 10, ends, START.plusSeconds(10 * 60 + 1)));

        Instant shortSlot = InterviewSessionRules.endsAt(START, 5);
        assertEquals("SLOT_EXPIRED",
                InterviewSessionRules.firstJoinDenial(START, 10, shortSlot, START.plusSeconds(6 * 60)));
    }

    @Test
    void parallelJoinIsRejectedUntilTheLockIsStale() {
        assertEquals("REJECT_PARALLEL", InterviewSessionRules.resolveLock("IN_PROGRESS", false, false));
        assertEquals("RECLAIM", InterviewSessionRules.resolveLock("IN_PROGRESS", false, true));
        assertEquals("RESUME_SAME_CLIENT", InterviewSessionRules.resolveLock("WINDING_DOWN", true, false));
        assertTrue(InterviewSessionRules.lockIsStale(START, START.plusSeconds(120)));
        assertFalse(InterviewSessionRules.lockIsStale(START, START.plusSeconds(119)));
    }
}

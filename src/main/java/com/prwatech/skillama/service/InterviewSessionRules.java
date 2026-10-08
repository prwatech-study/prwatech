package com.prwatech.skillama.service;

import java.time.Duration;
import java.time.Instant;

/**
 * Wall-clock rules shared by org interviews. The slot end is scheduledAt + duration
 * and does not pause while the candidate is offline.
 */
public final class InterviewSessionRules {

    public static final int DEFAULT_DURATION_MINUTES = 20;
    public static final int DEFAULT_JOIN_GRACE_MINUTES = 10;
    public static final int STALE_LOCK_SECONDS = 120;
    public static final double CREDITS_PER_MINUTE = 1.0;
    public static final String CLOSE_TEXT =
            "We're at the end of our scheduled time. Thank you for speaking with me today.";

    private InterviewSessionRules() {
    }

    public static Instant endsAt(Instant scheduledAt, int durationMinutes) {
        return scheduledAt.plus(Duration.ofMinutes(durationMinutes));
    }

    /** Whole seconds until endsAt, rounded up, never negative. */
    public static long remainingSeconds(Instant endsAt, Instant now) {
        if (endsAt == null || now == null) {
            return 0;
        }
        long millis = Duration.between(now, endsAt).toMillis();
        if (millis <= 0) {
            return 0;
        }
        return (millis + 999) / 1000;
    }

    /**
     * First start window is [scheduledAt, scheduledAt + joinGrace], and also before slot end.
     * Equality at the grace boundary is still inside the window (matches the LMS helper).
     */
    public static String firstJoinDenial(Instant scheduledAt, int joinGraceMinutes, Instant slotEnd, Instant now) {
        if (scheduledAt == null || slotEnd == null || now == null || joinGraceMinutes < 0) {
            return "INVALID_SCHEDULE";
        }
        if (now.isBefore(scheduledAt)) {
            return "TOO_EARLY";
        }
        if (now.isAfter(scheduledAt.plus(Duration.ofMinutes(joinGraceMinutes)))) {
            return "JOIN_GRACE_EXPIRED";
        }
        if (!now.isBefore(slotEnd)) {
            return "SLOT_EXPIRED";
        }
        return null;
    }

    public static boolean lockIsStale(Instant lastHeartbeatAt, Instant now) {
        if (lastHeartbeatAt == null) {
            return true;
        }
        return Duration.between(lastHeartbeatAt, now).getSeconds() >= STALE_LOCK_SECONDS;
    }

    /**
     * @return CREATE, RESUME_SAME_CLIENT, RECLAIM, or REJECT_PARALLEL
     */
    public static String resolveLock(String sessionStatus, boolean lockOwnerMatches, boolean lockStale) {
        boolean live = "IN_PROGRESS".equals(sessionStatus) || "WINDING_DOWN".equals(sessionStatus);
        if (!live) {
            return "CREATE";
        }
        if (lockOwnerMatches) {
            return "RESUME_SAME_CLIENT";
        }
        if (lockStale) {
            return "RECLAIM";
        }
        return "REJECT_PARALLEL";
    }

    public static boolean timeUp(long remainingSeconds) {
        return remainingSeconds <= 0;
    }
}

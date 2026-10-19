package com.prwatech.skillama.service;

/**
 * One low-credit email per wallet ceiling. A recharge raises the ceiling, so the
 * next time usage crosses 75% a new email can go out.
 */
public final class LowCreditNotice {

    public static final double NEAR_LIMIT_RATIO = 0.75;

    private LowCreditNotice() {
    }

    public static boolean shouldNotify(double usedUsd, double limitUsd, Double notifiedAtLimitUsd) {
        if (limitUsd <= 0 || usedUsd < 0) {
            return false;
        }
        if (usedUsd / limitUsd < NEAR_LIMIT_RATIO) {
            return false;
        }
        if (notifiedAtLimitUsd == null) {
            return true;
        }
        return notifiedAtLimitUsd + 0.000001 < limitUsd;
    }

    /** Whole credits when the fraction is tiny, otherwise one decimal. */
    public static String formatCredits(double credits) {
        double rounded = Math.round(credits * 10.0) / 10.0;
        if (Math.abs(rounded - Math.rint(rounded)) < 0.05) {
            return String.valueOf((long) Math.rint(rounded));
        }
        return String.format(java.util.Locale.US, "%.1f", rounded);
    }
}

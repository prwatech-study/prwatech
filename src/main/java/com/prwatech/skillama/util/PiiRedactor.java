package com.prwatech.skillama.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Redacts PII from AI Mentor/Chat question+answer text before persistence. Mirrors
 * skillama-lms's redactPiiForStorage (src/utils/tutorQueryGuard.js) exactly — this is
 * the server-side counterpart now that question/answer generation moved server-side.
 */
public final class PiiRedactor {

    private static final Pattern EMAIL = Pattern.compile("\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SSN = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");
    private static final Pattern CARD_LIKE = Pattern.compile("\\b(?:\\d[ -]*?){13,19}\\b");
    private static final Pattern PHONE_LIKE =
            Pattern.compile("(?:\\+?\\d{1,3}[\\s.-]?)?(?:\\(?\\d{2,4}\\)?[\\s.-]?)?\\d{3,4}[\\s.-]?\\d{3,4}");

    private PiiRedactor() {
    }

    public static String redact(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }

        String out = text;
        out = EMAIL.matcher(out).replaceAll("[redacted-email]");
        out = SSN.matcher(out).replaceAll("[redacted-ssn]");
        out = replaceIfDigitCountInRange(out, CARD_LIKE, 13, 19, "[redacted-card]");
        out = replaceIfDigitCountAtLeast(out, PHONE_LIKE, 10, "[redacted-phone]");
        return out;
    }

    private static String replaceIfDigitCountInRange(String text, Pattern pattern, int min, int max, String replacement) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder result = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            long digitCount = matcher.group().chars().filter(Character::isDigit).count();
            result.append(text, last, matcher.start());
            result.append(digitCount >= min && digitCount <= max ? replacement : matcher.group());
            last = matcher.end();
        }
        result.append(text.substring(last));
        return result.toString();
    }

    private static String replaceIfDigitCountAtLeast(String text, Pattern pattern, int min, String replacement) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder result = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            long digitCount = matcher.group().chars().filter(Character::isDigit).count();
            result.append(text, last, matcher.start());
            result.append(digitCount >= min ? replacement : matcher.group());
            last = matcher.end();
        }
        result.append(text.substring(last));
        return result.toString();
    }
}

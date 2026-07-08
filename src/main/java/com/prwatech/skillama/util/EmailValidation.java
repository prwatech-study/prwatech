package com.prwatech.skillama.util;

import java.util.regex.Pattern;

public final class EmailValidation {

    private static final int MAX_EMAIL_LENGTH = 254;
    private static final Pattern EMAIL_FORMAT = Pattern.compile(
            "^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@"
                    + "[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?"
                    + "(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)+$");

    private EmailValidation() {}

    public static void assertValidFormat(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        String normalized = email.trim().toLowerCase();
        if (normalized.length() > MAX_EMAIL_LENGTH) {
            throw new IllegalArgumentException("Email address is too long");
        }
        if (normalized.contains(" ") || normalized.chars().filter(ch -> ch == '@').count() != 1) {
            throw new IllegalArgumentException("Enter a valid email address");
        }
        if (!EMAIL_FORMAT.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Enter a valid email address");
        }
    }

    public static void assertPasswordLength(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
    }
}

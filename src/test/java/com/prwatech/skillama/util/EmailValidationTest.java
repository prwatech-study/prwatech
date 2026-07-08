package com.prwatech.skillama.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmailValidationTest {

    @Test
    void assertValidFormat_acceptsValidEmail() {
        assertDoesNotThrow(() -> EmailValidation.assertValidFormat("user@example.com"));
    }

    @Test
    void assertValidFormat_rejectsInvalidEmail() {
        assertThrows(IllegalArgumentException.class, () -> EmailValidation.assertValidFormat("not-email"));
        assertThrows(IllegalArgumentException.class, () -> EmailValidation.assertValidFormat("a@b"));
    }

    @Test
    void assertPasswordLength_requiresEightChars() {
        assertDoesNotThrow(() -> EmailValidation.assertPasswordLength("12345678"));
        assertThrows(IllegalArgumentException.class, () -> EmailValidation.assertPasswordLength("short"));
    }
}

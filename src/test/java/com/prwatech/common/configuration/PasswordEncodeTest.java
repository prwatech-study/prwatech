package com.prwatech.common.configuration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PasswordEncodeTest {

    private static final String PRODUCTION_SALT = "$2a$10$HYunSfuYwLxf8CrqhW7QHO";
    private static final String FAST_TEST_SALT = "$2a$04$HYunSfuYwLxf8CrqhW7QHO";

    private PasswordEncode passwordEncode;

    @BeforeEach
    void setUp() {
        AppContext appContext = mock(AppContext.class);
        when(appContext.getSalt()).thenReturn(FAST_TEST_SALT);
        passwordEncode = new PasswordEncode(appContext);
    }

    @Test
    void resolveBcryptStrength_readsCostFromExistingSaltConfig() {
        assertEquals(10, PasswordEncode.resolveBcryptStrength(PRODUCTION_SALT));
        assertEquals(4, PasswordEncode.resolveBcryptStrength(FAST_TEST_SALT));
        assertEquals(PasswordEncode.DEFAULT_BCRYPT_STRENGTH, PasswordEncode.resolveBcryptStrength(null));
        assertEquals(PasswordEncode.DEFAULT_BCRYPT_STRENGTH, PasswordEncode.resolveBcryptStrength("not-a-salt"));
    }

    @Test
    void getEncryptedPassword_storesBcryptNotBase64() {
        String plain = "new-user-secret";
        String stored = passwordEncode.getEncryptedPassword(plain);

        assertTrue(passwordEncode.isBcryptHash(stored));
        assertFalse(passwordEncode.isLegacyEncoded(stored));
        String base64 = Base64.getEncoder().encodeToString(plain.getBytes(StandardCharsets.UTF_8));
        assertFalse(stored.equals(base64));
        assertFalse(stored.equals(plain));
    }

    @Test
    void compare_acceptsCorrectBcryptPassword() {
        String stored = passwordEncode.getEncryptedPassword("correct-pass");
        assertTrue(passwordEncode.compare("correct-pass", stored));
    }

    @Test
    void compare_rejectsIncorrectBcryptPassword() {
        String stored = passwordEncode.getEncryptedPassword("correct-pass");
        assertFalse(passwordEncode.compare("wrong-pass", stored));
    }

    @Test
    void compare_acceptsLegacyBase64Password() {
        String legacy = Base64.getEncoder().encodeToString("legacy-secret".getBytes(StandardCharsets.UTF_8));
        assertTrue(passwordEncode.isLegacyEncoded(legacy));
        assertFalse(passwordEncode.isBcryptHash(legacy));
        assertTrue(passwordEncode.compare("legacy-secret", legacy));
        assertFalse(passwordEncode.compare("wrong-secret", legacy));
    }

    @Test
    void isBcryptHash_matchesConfiguredBcryptFamily() {
        assertTrue(passwordEncode.isBcryptHash(
                "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"));
        assertFalse(passwordEncode.isBcryptHash("c2VjcmV0")); // Base64 of "secret"
        assertFalse(passwordEncode.isBcryptHash(null));
    }

    @Test
    void migrateStoredPassword_upgradesBase64ToBcrypt() {
        String plain = "legacy-secret";
        String legacy = Base64.getEncoder().encodeToString(plain.getBytes(StandardCharsets.UTF_8));

        String migrated = passwordEncode.migrateStoredPassword(legacy);

        assertTrue(passwordEncode.isBcryptHash(migrated));
        assertTrue(passwordEncode.compare(plain, migrated));
        assertFalse(migrated.equals(legacy));
    }

    @Test
    void migrateStoredPassword_leavesBcryptUnchanged() {
        String hash = passwordEncode.getEncryptedPassword("already-hashed");
        assertEquals(hash, passwordEncode.migrateStoredPassword(hash));
    }
}

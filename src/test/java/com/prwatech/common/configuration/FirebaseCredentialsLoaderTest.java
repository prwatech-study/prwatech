package com.prwatech.common.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FirebaseCredentialsLoaderTest {

    @Test
    void prefersJsonOverFile() throws Exception {
        try (InputStream stream = FirebaseCredentialsLoader.open("{\"type\":\"service_account\"}", "/does/not/exist.json")) {
            String body = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals("{\"type\":\"service_account\"}", body);
        }
    }

    @Test
    void readsExternalFile(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("firebase-service-account.json");
        Files.writeString(file, "{\"project_id\":\"example\"}");
        try (InputStream stream = FirebaseCredentialsLoader.open("  ", file.toString())) {
            String body = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals("{\"project_id\":\"example\"}", body);
        }
    }

    @Test
    void missingConfigThrowsWithoutSecretMaterial() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class, () -> FirebaseCredentialsLoader.open("", ""));
        assertTrue(ex.getMessage().contains("FIREBASE_CREDENTIALS_FILE"));
        assertFalseContainsPrivateKeyMaterial(ex.getMessage());
    }

    @Test
    void missingFileThrowsWithoutPathContentsLeakOfKey() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> FirebaseCredentialsLoader.open(null, "/tmp/missing-firebase-creds.json"));
        assertTrue(ex.getMessage().contains("FIREBASE_CREDENTIALS_FILE"));
        assertFalseContainsPrivateKeyMaterial(ex.getMessage());
    }

    private static void assertFalseContainsPrivateKeyMaterial(String message) {
        String lower = message.toLowerCase();
        org.junit.jupiter.api.Assertions.assertFalse(lower.contains("begin private key"));
        org.junit.jupiter.api.Assertions.assertFalse(lower.contains("private_key"));
    }
}

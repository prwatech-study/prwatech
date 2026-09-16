package com.prwatech.common.configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves Firebase service-account credentials from environment/config.
 * Never logs the JSON or private key.
 */
final class FirebaseCredentialsLoader {

  private FirebaseCredentialsLoader() {}

  static InputStream open(String credentialsJson, String credentialsFile) throws IOException {
    if (credentialsJson != null && !credentialsJson.isBlank()) {
      return new ByteArrayInputStream(credentialsJson.getBytes(StandardCharsets.UTF_8));
    }
    if (credentialsFile != null && !credentialsFile.isBlank()) {
      return openFile(credentialsFile, "FIREBASE_CREDENTIALS_FILE");
    }
    String adc = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
    if (adc != null && !adc.isBlank()) {
      return openFile(adc, "GOOGLE_APPLICATION_CREDENTIALS");
    }
    throw new IllegalStateException(
        "Firebase credentials are not configured. Set FIREBASE_CREDENTIALS_FILE "
            + "to a gitignored service-account JSON path, or FIREBASE_CREDENTIALS_JSON.");
  }

  private static InputStream openFile(String path, String source) throws IOException {
    Path file = Path.of(path);
    if (!Files.isRegularFile(file)) {
      throw new IllegalStateException(source + " does not point to a readable credentials file.");
    }
    return Files.newInputStream(file);
  }
}

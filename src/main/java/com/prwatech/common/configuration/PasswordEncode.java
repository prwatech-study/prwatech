package com.prwatech.common.configuration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.regex.Pattern;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Hashes and verifies credentials. New values are bcrypt (cost taken from
 * {@code bcrypt.password.salt}); stored Base64 values are still accepted so
 * existing users can log in and be upgraded.
 *
 * The configured salt string is used only for bcrypt <em>cost</em> and hash-format
 * detection ({@code $2a$10$...}). A shared salt is not applied to every hash —
 * bcrypt already embeds a unique salt per password.
 */
@Component
public class PasswordEncode {

  static final int DEFAULT_BCRYPT_STRENGTH = 10;

  /** Matches the bcrypt family already used by {@code bcrypt.password.salt} ({@code $2a$10$...}). */
  private static final Pattern BCRYPT_HASH =
      Pattern.compile("^\\$2[abxy]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");

  /** Same charset check previously used to detect Base64-stored passwords. */
  private static final Pattern LEGACY_BASE64 = Pattern.compile("^[A-Za-z0-9+/=]+$");

  private final BCryptPasswordEncoder bcryptPasswordEncoder;

  public PasswordEncode(AppContext appContext) {
    this.bcryptPasswordEncoder =
        new BCryptPasswordEncoder(resolveBcryptStrength(appContext != null ? appContext.getSalt() : null));
  }

  public String getEncryptedPassword(String password) {
    return bcryptPasswordEncoder.encode(password);
  }

  public Boolean compare(String password, String encodedPassword) {
    if (password == null || encodedPassword == null || encodedPassword.isEmpty()) {
      return false;
    }
    if (isBcryptHash(encodedPassword)) {
      try {
        return bcryptPasswordEncoder.matches(password, encodedPassword);
      } catch (IllegalArgumentException e) {
        return false;
      }
    }
    if (isLegacyEncoded(encodedPassword)) {
      try {
        byte[] decoded = Base64.getDecoder().decode(encodedPassword);
        byte[] expected = new String(decoded, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8);
        byte[] actual = password.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
      } catch (IllegalArgumentException e) {
        return false;
      }
    }
    return false;
  }

  public boolean isBcryptHash(String encoded) {
    return encoded != null && BCRYPT_HASH.matcher(encoded).matches();
  }

  /**
   * True when the stored value is the pre-bcrypt Base64 format (not a bcrypt hash).
   * Uses the existing Base64 charset / padding heuristic, then a decode check —
   * not string length alone.
   */
  public boolean isLegacyEncoded(String encoded) {
    if (encoded == null || encoded.isEmpty() || isBcryptHash(encoded)) {
      return false;
    }
    if (!LEGACY_BASE64.matcher(encoded).matches() || encoded.length() % 4 != 0) {
      return false;
    }
    try {
      Base64.getDecoder().decode(encoded);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  /**
   * Re-hashes a legacy Base64 stored password to bcrypt. Already-bcrypt values are
   * returned unchanged. Does not log the stored value or the decoded plaintext.
   */
  public String migrateStoredPassword(String stored) {
    if (stored == null || stored.isEmpty() || isBcryptHash(stored) || !isLegacyEncoded(stored)) {
      return stored;
    }
    byte[] decoded = Base64.getDecoder().decode(stored);
    String plaintext = new String(decoded, StandardCharsets.UTF_8);
    return getEncryptedPassword(plaintext);
  }

  static int resolveBcryptStrength(String configuredSalt) {
    if (configuredSalt != null && configuredSalt.length() >= 6
        && configuredSalt.charAt(0) == '$' && configuredSalt.charAt(3) == '$'
        && configuredSalt.charAt(6) == '$') {
      try {
        int strength = Integer.parseInt(configuredSalt.substring(4, 6));
        if (strength >= 4 && strength <= 31) {
          return strength;
        }
      } catch (NumberFormatException ignored) {
        // fall through to default
      }
    }
    return DEFAULT_BCRYPT_STRENGTH;
  }
}

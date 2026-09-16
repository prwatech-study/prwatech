package com.prwatech.common.configuration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guardrail: live credentials must not be committed. Values are never asserted —
 * only formats and placeholder usage.
 */
class CommittedSecretsScanTest {

    private static final Path ROOT = Path.of("").toAbsolutePath();
    private static final Pattern MONGO_EMBEDDED_CREDS =
            Pattern.compile("mongodb(\\+srv)?://[^$\\s/:]+:[^$\\s/@]+@");
    private static final Pattern OPENAI_PROJECT_KEY = Pattern.compile("sk-proj-");
    private static final Pattern RAZORPAY_LIVE_KEY = Pattern.compile("rzp_live_[A-Za-z0-9]+");
    private static final Pattern TWILIO_SID = Pattern.compile("=AC[a-f0-9]{32}");
    private static final Pattern HEX_SECRET_ASSIGNMENT =
            Pattern.compile("(?m)^[^#\\n]*internal-api-key=[0-9a-f]{32,}");

    @Test
    void applicationProperties_usesEnvPlaceholdersForSecrets() throws Exception {
        String content = Files.readString(ROOT.resolve("src/main/resources/application.properties"));

        assertTrue(content.contains("${SPRING_DATA_MONGODB_URI"));
        assertTrue(content.contains("${SKILLAMA_MONGODB_URI"));
        assertTrue(content.contains("${JWT_SECRET_KEY"));
        assertTrue(content.contains("${PRWATECH_AUTH0_CLIENT_SECRET"));
        assertTrue(content.contains("${PRWATECH_RZ_SECRET"));
        assertTrue(content.contains("${PRWATECH_TWILIO_AUTH_TOKEN"));
        assertTrue(content.contains("${SPRING_MAIL_PASSWORD"));
        assertTrue(content.contains("${AI_USAGE_API_KEY"));
        assertTrue(content.contains("${SWAGGER_PASSWORD"));
        assertTrue(content.contains("${FIREBASE_CREDENTIALS_FILE"));
        assertTrue(content.contains("${FIREBASE_CREDENTIALS_JSON"));

        assertFalse(MONGO_EMBEDDED_CREDS.matcher(content).find(),
                "Mongo URI must not embed user:password credentials");
        assertFalse(OPENAI_PROJECT_KEY.matcher(content).find());
        assertFalse(RAZORPAY_LIVE_KEY.matcher(content).find());
        assertFalse(TWILIO_SID.matcher(content).find());
        assertFalse(HEX_SECRET_ASSIGNMENT.matcher(content).find());
        assertFalse(content.contains("{noop}"));
    }

    @Test
    void securityConfig_doesNotHardcodeSwaggerPasswords() throws Exception {
        String content = Files.readString(
                ROOT.resolve("src/main/java/com/prwatech/common/configuration/SecurityConfig.java"));
        assertTrue(content.contains("${swagger.password"));
        assertFalse(content.contains("{noop}prwatech"));
        assertFalse(Pattern.compile("\\.withUser\\(\"[a-z]+\"\\)\\.password\\(\"\\{noop\\}").matcher(content).find());
    }

    @Test
    void envExample_existsAndContainsNoLiveSecrets() throws Exception {
        Path example = ROOT.resolve(".env.example");
        assertTrue(Files.exists(example));
        String content = Files.readString(example);
        assertFalse(MONGO_EMBEDDED_CREDS.matcher(content).find());
        assertFalse(OPENAI_PROJECT_KEY.matcher(content).find());
        assertFalse(RAZORPAY_LIVE_KEY.matcher(content).find());
        assertTrue(content.contains("JWT_SECRET_KEY="));
        assertTrue(content.contains("SKILLAMA_MONGODB_URI="));
        assertTrue(content.contains("AI_USAGE_API_KEY="));
        assertTrue(content.contains("FIREBASE_CREDENTIALS_FILE="));
        for (String line : List.of(content.split("\n"))) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                continue;
            }
            String value = trimmed.substring(trimmed.indexOf('=') + 1).trim();
            assertTrue(value.isEmpty()
                            || value.startsWith("mongodb://localhost")
                            || value.startsWith("local-")
                            || value.equals("dev")
                            || value.equals("dev-only-change-me")
                            || value.equals("true")
                            || value.equals("false"),
                    "example env must not contain a live secret value");
        }
    }

    @Test
    void swaggerPasswordEncoderId_prefixesNoopForPlainLocalPassword() {
        assertTrue(SecurityConfig.swaggerPasswordEncoderId("dev-only-change-me").startsWith("{noop}"));
        assertTrue(SecurityConfig.swaggerPasswordEncoderId("{bcrypt}abc").startsWith("{bcrypt}"));
    }

    @Test
    void firebaseServiceAccountJson_isNotOnClasspath() throws Exception {
        Path leaked = ROOT.resolve("src/main/resources/templates/firbase_sdk.json");
        org.junit.jupiter.api.Assertions.assertFalse(Files.exists(leaked));
        Path resources = ROOT.resolve("src/main/resources");
        try (var paths = Files.walk(resources)) {
            boolean pem = paths
                    .filter(Files::isRegularFile)
                    .anyMatch(path -> {
                        try {
                            return Files.readString(path).contains("BEGIN PRIVATE KEY");
                        } catch (Exception e) {
                            return false;
                        }
                    });
            org.junit.jupiter.api.Assertions.assertFalse(pem, "resources must not contain a private key PEM");
        }
        String beanConfig = Files.readString(
                ROOT.resolve("src/main/java/com/prwatech/common/configuration/BeanConfiguration.java"));
        org.junit.jupiter.api.Assertions.assertFalse(beanConfig.contains("firbase_sdk.json"));
        Path example = ROOT.resolve("firebase-service-account.json.example");
        org.junit.jupiter.api.Assertions.assertTrue(Files.exists(example));
        String exampleText = Files.readString(example);
        org.junit.jupiter.api.Assertions.assertTrue(exampleText.contains("YOUR_FIREBASE_PROJECT_ID"));
        org.junit.jupiter.api.Assertions.assertFalse(exampleText.contains("infra-hulling"));
    }
}

package com.prwatech.skillama.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillamaCorsOriginsTest {

    private static final List<String> ALLOWED = SkillamaCorsOrigins.parse(
            "https://skillama.co.in,https://dev.skillama.co.in,http://localhost:3000");

    @Test
    void listedOriginIsAllowed() {
        assertTrue(SkillamaCorsOrigins.isAllowed("https://skillama.co.in", ALLOWED));
        assertTrue(SkillamaCorsOrigins.isAllowed("https://Skillama.co.in", ALLOWED));
        assertTrue(SkillamaCorsOrigins.isAllowed("http://localhost:3000", ALLOWED));
    }

    @Test
    void unknownAndSpoofedOriginsAreRejected() {
        assertFalse(SkillamaCorsOrigins.isAllowed("https://evil.com", ALLOWED));
        assertFalse(SkillamaCorsOrigins.isAllowed("https://skillama.co.in.attacker.com", ALLOWED));
        assertFalse(SkillamaCorsOrigins.isAllowed("https://notskillama.co.in", ALLOWED));
        assertFalse(SkillamaCorsOrigins.isAllowed("https://foo.bar.skillama.co.in", ALLOWED));
        assertFalse(SkillamaCorsOrigins.isAllowed("https://acme.skillama.co.in", ALLOWED));
        assertFalse(SkillamaCorsOrigins.isAllowed(null, ALLOWED));
    }

    @Test
    void defaultOriginsDoNotIncludeTenantSubdomains() {
        List<String> defaults = SkillamaCorsOrigins.parse(SkillamaCorsOrigins.DEFAULT);
        assertTrue(defaults.contains("https://skillama.co.in"));
        assertTrue(defaults.contains("https://www.skillama.co.in"));
        assertTrue(defaults.contains("https://dev.skillama.co.in"));
        assertTrue(defaults.contains("http://localhost:3000"));
        assertTrue(defaults.contains("http://localhost:3111"));
        assertFalse(defaults.stream().anyMatch(o -> o.contains("acme.skillama")));
        assertFalse(SkillamaCorsOrigins.isAllowed("https://acme.skillama.co.in", defaults));
    }

    @Test
    void corsConfigurationReflectsOnlyAllowedOrigin() {
        MockHttpServletRequest allowed = new MockHttpServletRequest();
        allowed.addHeader("Origin", "https://skillama.co.in");
        CorsConfiguration ok = SkillamaCorsConfiguration.corsFor(allowed, ALLOWED);
        assertEquals(List.of("https://skillama.co.in"), ok.getAllowedOrigins());
        assertEquals(Boolean.TRUE, ok.getAllowCredentials());

        MockHttpServletRequest denied = new MockHttpServletRequest();
        denied.addHeader("Origin", "https://evil.com");
        CorsConfiguration bad = SkillamaCorsConfiguration.corsFor(denied, ALLOWED);
        assertNull(bad.checkOrigin("https://evil.com"));
    }
}

package com.prwatech.common.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpsEnforcementTest {

    @Test
    void redirectsPublicHttpWhenProxyReportsHttp() {
        assertEquals(
                "https://prwatech.xyz/skillama/api/health?ready=1",
                HttpsEnforcement.redirectLocation(
                        "http://prwatech.xyz/skillama/api/health",
                        "ready=1",
                        "http",
                        "prwatech.xyz"));
    }

    @Test
    void doesNotRedirectLocalhost() {
        assertNull(
                HttpsEnforcement.redirectLocation(
                        "http://localhost:9090/skillama/api/health",
                        null,
                        "http",
                        "localhost:9090"));
    }

    @Test
    void doesNotRedirectWhenEdgeAlreadyUsedHttps() {
        assertNull(
                HttpsEnforcement.redirectLocation(
                        "http://prwatech.xyz/skillama/api/health",
                        null,
                        "https",
                        "prwatech.xyz"));
    }

    @Test
    void doesNotRedirectWhenForwardedProtoIsMissing() {
        assertNull(
                HttpsEnforcement.redirectLocation(
                        "http://prwatech.xyz/skillama/api/health",
                        null,
                        null,
                        "prwatech.xyz"));
    }

    @Test
    void stripsDefaultHttpPortOnRedirect() {
        assertEquals(
                "https://prwatech.xyz/skillama/",
                HttpsEnforcement.redirectLocation(
                        "http://prwatech.xyz:80/skillama/",
                        null,
                        "http",
                        "prwatech.xyz"));
    }

    @Test
    void clientProtocolPrefersFirstForwardedHop() {
        assertEquals("https", HttpsEnforcement.clientProtocol("https, http"));
        assertEquals("http", HttpsEnforcement.clientProtocol("http"));
        assertEquals("", HttpsEnforcement.clientProtocol(null));
        assertTrue(HttpsEnforcement.isHttps("https"));
        assertFalse(HttpsEnforcement.isHttps("http"));
    }

    @Test
    void treatsLoopbackAsLocal() {
        assertTrue(HttpsEnforcement.isLocalHost("localhost:9090"));
        assertTrue(HttpsEnforcement.isLocalHost("127.0.0.1"));
        assertFalse(HttpsEnforcement.isLocalHost("prwatech.xyz"));
    }
}

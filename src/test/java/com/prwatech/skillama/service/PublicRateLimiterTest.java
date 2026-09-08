package com.prwatech.skillama.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicRateLimiterTest {

    private PublicRateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new PublicRateLimiter();
    }

    @Test
    void allowsUpToTheBurstThenBlocks() {
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire("caller-a", 5), "request " + i + " should be allowed");
        }
        assertFalse(limiter.tryAcquire("caller-a", 5));
    }

    @Test
    void callersAreTrackedIndependently() {
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire("caller-a", 5));
        }
        assertFalse(limiter.tryAcquire("caller-a", 5));
        assertTrue(limiter.tryAcquire("caller-b", 5), "one caller must not exhaust another's budget");
    }

    @Test
    void scopesAreTrackedIndependently() {
        for (int i = 0; i < 3; i++) {
            assertTrue(limiter.tryAcquire("branding:1.2.3.4", 3));
        }
        assertFalse(limiter.tryAcquire("branding:1.2.3.4", 3));
        assertTrue(limiter.tryAcquire("resolve:1.2.3.4", 3));
    }

    @Test
    void nonPositiveLimitDisablesLimiting() {
        for (int i = 0; i < 1000; i++) {
            assertTrue(limiter.tryAcquire("caller-a", 0));
        }
        assertTrue(limiter.tryAcquire("caller-a", -1));
    }

    @Test
    void refillsProportionallyToElapsedTime() {
        AtomicLong clock = new AtomicLong(0);
        PublicRateLimiter timed = new PublicRateLimiter(clock::get);

        for (int i = 0; i < 60; i++) {
            assertTrue(timed.tryAcquire("caller-a", 60));
        }
        assertFalse(timed.tryAcquire("caller-a", 60), "burst should be exhausted");

        // A tenth of a minute at 60/min restores six permits, and no more.
        clock.addAndGet(TimeUnit.SECONDS.toNanos(6));
        for (int i = 0; i < 6; i++) {
            assertTrue(timed.tryAcquire("caller-a", 60), "permit " + i + " should have refilled");
        }
        assertFalse(timed.tryAcquire("caller-a", 60), "refill must not exceed elapsed time");
    }

    @Test
    void refillIsCappedAtTheBurstSize() {
        AtomicLong clock = new AtomicLong(0);
        PublicRateLimiter timed = new PublicRateLimiter(clock::get);

        assertTrue(timed.tryAcquire("caller-a", 10));

        // An hour of idle time must not accumulate more than one full bucket.
        clock.addAndGet(TimeUnit.HOURS.toNanos(1));
        for (int i = 0; i < 10; i++) {
            assertTrue(timed.tryAcquire("caller-a", 10));
        }
        assertFalse(timed.tryAcquire("caller-a", 10));
    }

    @Test
    void callerKeyPrefersFirstForwardedHop() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.7, 70.41.3.18, 150.172.238.178");
        assertEquals("203.0.113.7", PublicRateLimiter.callerKey(request));
    }

    @Test
    void callerKeyFallsBackThroughRealIpToRemoteAddr() {
        MockHttpServletRequest withRealIp = new MockHttpServletRequest();
        withRealIp.setRemoteAddr("10.0.0.1");
        withRealIp.addHeader("X-Real-IP", "203.0.113.9");
        assertEquals("203.0.113.9", PublicRateLimiter.callerKey(withRealIp));

        MockHttpServletRequest bare = new MockHttpServletRequest();
        bare.setRemoteAddr("10.0.0.2");
        assertEquals("10.0.0.2", PublicRateLimiter.callerKey(bare));
    }

    @Test
    void callerKeyIgnoresBlankForwardedHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.3");
        request.addHeader("X-Forwarded-For", "   ");
        assertEquals("10.0.0.3", PublicRateLimiter.callerKey(request));
    }

    @Test
    void callerKeyHandlesNullRequest() {
        assertEquals("unknown", PublicRateLimiter.callerKey(null));
    }
}

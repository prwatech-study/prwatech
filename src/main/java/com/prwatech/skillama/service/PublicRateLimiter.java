package com.prwatech.skillama.service;

import javax.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Token bucket for unauthenticated endpoints, keyed per caller.
 *
 * <p>Counters are per instance, so behind multiple replicas the effective ceiling is the
 * configured rate times the replica count. That is acceptable for the goal here — making
 * tenant enumeration slow rather than free — but it is not a substitute for an edge or
 * gateway limit if you need a hard global cap.
 */
@Component
public class PublicRateLimiter {

    /** Bounds memory when a caller rotates source addresses. */
    private static final int MAX_TRACKED_CALLERS = 50_000;

    private static final long IDLE_EVICTION_NANOS = TimeUnit.MINUTES.toNanos(10);

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    private final LongSupplier nanoTimeSource;

    public PublicRateLimiter() {
        this(System::nanoTime);
    }

    /** Visible for tests so refill behaviour can be exercised without sleeping. */
    PublicRateLimiter(LongSupplier nanoTimeSource) {
        this.nanoTimeSource = nanoTimeSource;
    }

    /**
     * @param permitsPerMinute sustained rate, also used as the burst size. Zero or less
     *                         disables limiting, so the feature can be turned off by config.
     * @return true when the request may proceed
     */
    public boolean tryAcquire(String key, int permitsPerMinute) {
        if (permitsPerMinute <= 0) {
            return true;
        }
        long now = nanoTimeSource.getAsLong();
        evictIfCrowded(now);
        Bucket bucket = buckets.computeIfAbsent(key, ignored -> new Bucket(permitsPerMinute, now));
        return bucket.tryAcquire(permitsPerMinute, now);
    }

    /**
     * Prefers the first {@code X-Forwarded-For} hop because these endpoints sit behind a
     * proxy, where {@code getRemoteAddr()} would collapse every caller onto one key.
     */
    public static String callerKey(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            String first = forwarded.split(",")[0].trim();
            if (StringUtils.hasText(first)) {
                return first;
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        String remote = request.getRemoteAddr();
        return StringUtils.hasText(remote) ? remote : "unknown";
    }

    private void evictIfCrowded(long now) {
        if (buckets.size() < MAX_TRACKED_CALLERS) {
            return;
        }
        Iterator<Map.Entry<String, Bucket>> it = buckets.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().isIdleSince(now)) {
                it.remove();
            }
        }
        // Still full of active callers: drop everything rather than grow without bound.
        if (buckets.size() >= MAX_TRACKED_CALLERS) {
            buckets.clear();
        }
    }

    private static final class Bucket {
        private double tokens;
        private long lastRefillNanos;

        Bucket(int capacity, long now) {
            this.tokens = capacity;
            this.lastRefillNanos = now;
        }

        synchronized boolean tryAcquire(int permitsPerMinute, long now) {
            double elapsedMinutes = (now - lastRefillNanos) / (double) TimeUnit.MINUTES.toNanos(1);
            if (elapsedMinutes > 0) {
                tokens = Math.min(permitsPerMinute, tokens + elapsedMinutes * permitsPerMinute);
                lastRefillNanos = now;
            }
            if (tokens < 1.0d) {
                return false;
            }
            tokens -= 1.0d;
            return true;
        }

        synchronized boolean isIdleSince(long now) {
            return now - lastRefillNanos > IDLE_EVICTION_NANOS;
        }
    }
}

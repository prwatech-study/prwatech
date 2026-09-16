package com.prwatech.skillama.security;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Exact CORS origin allowlist. No {@code *} and no automatic {@code *.skillama.co.in}.
 */
public final class SkillamaCorsOrigins {

    public static final String DEFAULT =
            "https://skillama.co.in,https://www.skillama.co.in,https://dev.skillama.co.in,"
                    + "http://localhost:3000,http://localhost:3111";

    private SkillamaCorsOrigins() {
    }

    public static List<String> parse(String raw) {
        List<String> origins = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return origins;
        }
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String normalized = normalize(trimmed);
            if (normalized != null) {
                origins.add(normalized);
            }
        }
        return origins;
    }

    public static boolean isAllowed(String requestOrigin, List<String> allowed) {
        String normalized = normalize(requestOrigin);
        if (normalized == null) {
            return false;
        }
        for (String origin : allowed) {
            if (normalized.equals(origin)) {
                return true;
            }
        }
        return false;
    }

    /**
     * scheme://host with default ports omitted; host compared case-insensitively.
     */
    public static String normalize(String origin) {
        if (origin == null || origin.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(origin.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) {
                return null;
            }
            scheme = scheme.toLowerCase(Locale.ROOT);
            host = host.toLowerCase(Locale.ROOT);
            int port = uri.getPort();
            if (port == -1
                    || ("http".equals(scheme) && port == 80)
                    || ("https".equals(scheme) && port == 443)) {
                return scheme + "://" + host;
            }
            return scheme + "://" + host + ":" + port;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

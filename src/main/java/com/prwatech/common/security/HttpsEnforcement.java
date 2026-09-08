package com.prwatech.common.security;

/**
 * Browser-facing HTTPS rules for requests that sit behind nginx (or similar).
 *
 * <p>Redirects only when {@code X-Forwarded-Proto} is {@code http}, so the internal
 * HTTP hop from the TLS-terminating proxy cannot 308-loop.
 */
public final class HttpsEnforcement {

    public static final String HSTS_HEADER_VALUE = "max-age=31536000; includeSubDomains";

    private HttpsEnforcement() {}

    public static String hostname(String hostHeader) {
        if (hostHeader == null || hostHeader.isBlank()) {
            return "";
        }
        String host = hostHeader.trim().toLowerCase();
        if (host.startsWith("[")) {
            int end = host.indexOf(']');
            return end >= 0 ? host.substring(1, end) : host;
        }
        int colon = host.indexOf(':');
        return colon >= 0 ? host.substring(0, colon) : host;
    }

    public static boolean isLocalHost(String hostHeader) {
        String host = hostname(hostHeader);
        return host.isEmpty()
                || "localhost".equals(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host)
                || host.endsWith(".localhost")
                || host.endsWith(".local");
    }

    /**
     * @return {@code http}, {@code https}, or empty when the edge protocol is unknown
     */
    public static String clientProtocol(String forwardedProto) {
        if (forwardedProto == null || forwardedProto.isBlank()) {
            return "";
        }
        String first = forwardedProto.split(",")[0].trim().toLowerCase();
        if ("http".equals(first) || "https".equals(first)) {
            return first;
        }
        return "";
    }

    public static boolean isHttps(String forwardedProto) {
        return "https".equals(clientProtocol(forwardedProto));
    }

    /**
     * Absolute HTTPS location to 308 to, or {@code null} when the request should proceed.
     */
    public static String redirectLocation(
            String requestUrl, String queryString, String forwardedProto, String hostHeader) {
        if (isLocalHost(hostHeader)) {
            return null;
        }
        if (!"http".equals(clientProtocol(forwardedProto))) {
            return null;
        }
        if (requestUrl == null || requestUrl.isBlank()) {
            return null;
        }
        String httpsUrl;
        if (requestUrl.startsWith("http://")) {
            httpsUrl = "https://" + requestUrl.substring("http://".length());
        } else if (requestUrl.startsWith("https://")) {
            return null;
        } else {
            return null;
        }
        httpsUrl = httpsUrl.replaceFirst("^https://([^/]+):80(?=/|$)", "https://$1");
        if (queryString != null && !queryString.isBlank()) {
            httpsUrl += "?" + queryString;
        }
        return httpsUrl;
    }
}

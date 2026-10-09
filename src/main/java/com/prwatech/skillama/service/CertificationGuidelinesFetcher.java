package com.prwatech.skillama.service;

import com.prwatech.skillama.model.CertificationExamMeta;
import com.prwatech.skillama.util.IndiaTime;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches an official certification guidelines page and extracts plain text + structured exam meta.
 */
@Service
@Slf4j
public class CertificationGuidelinesFetcher {

    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int MAX_BODY_BYTES = 2_000_000;
    private static final int MAX_SNAPSHOT_CHARS = 40_000;
    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; SkillamaGuidelinesBot/1.0; +https://skillama.com)";

    private static final Pattern RENEWAL_CUT_PATTERN = Pattern.compile(
            "(?i)renewal\\s+(?:exam|via|faqs)|renewal exam information");
    private static final Pattern DURATION_HOURS_PATTERN = Pattern.compile(
            "(?i)(?:length|duration)\\s*:?\\s*(\\d+|one|two|three)\\s*-?\\s*hours?");
    private static final Pattern DURATION_MINUTES_PATTERN = Pattern.compile(
            "(?i)(?:length|duration)\\s*:?\\s*(\\d+)\\s*minutes?");
    private static final Pattern QUESTION_RANGE_PATTERN = Pattern.compile(
            "(?i)(?:~\\s*)?(\\d+)\\s*[-–—to]+\\s*(\\d+)\\s*(?:multiple[- ]choice|questions?)");
    private static final Pattern QUESTION_SINGLE_PATTERN = Pattern.compile(
            "(?i)~?\\s*(\\d+)\\s*(?:multiple[- ]choice|multiple[- ]select)?\\s*(?:and\\s+multiple[- ]select\\s+)?questions?");

    private static final Pattern SECTION_DOMAIN_PATTERN = Pattern.compile(
            "(?i)^\\s*section\\s+\\d+\\s*[:.\\-–—]|\\(\\s*~?\\s*\\d{1,3}\\s*%");
    private static final List<String> JUNK_DOMAIN_MARKERS = List.of(
            "webinar", "onair", "register", "sign up", "sign in", "prepare with", "watch ",
            "learn more", "view faq", "faq", "schedule your exam", "schedule an exam", "renew",
            "recertif", "learning path", "skills boost", "get started", "explore ", "find a ",
            "contact us", "privacy", "terms of service", "cookie");

    public record FetchResult(String snapshot, CertificationExamMeta meta) {}

    public FetchResult fetch(String guidelinesUrl) {
        if (!StringUtils.hasText(guidelinesUrl)) {
            throw new IllegalArgumentException("guidelinesUrl is required");
        }
        String url = guidelinesUrl.trim();
        validateHttpUrl(url);

        Document doc;
        try {
            doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(CONNECT_TIMEOUT_MS)
                    .maxBodySize(MAX_BODY_BYTES)
                    .followRedirects(true)
                    .ignoreHttpErrors(false)
                    .get();
        } catch (IOException e) {
            log.warn("Failed to fetch certification guidelines from {}", url, e);
            throw new IllegalStateException(
                    "Could not fetch guidelines from the URL. Check the link and try again.", e);
        }

        for (Element el : doc.select("script, style, noscript, svg, nav, footer, header")) {
            el.remove();
        }
        String text = doc.body() != null ? doc.body().text() : doc.text();
        if (!StringUtils.hasText(text) || text.trim().length() < 80) {
            throw new IllegalStateException("Guidelines page returned too little content to use.");
        }
        String snapshot = text.trim();
        if (snapshot.length() > MAX_SNAPSHOT_CHARS) {
            snapshot = snapshot.substring(0, MAX_SNAPSHOT_CHARS);
        }

        CertificationExamMeta parsed = parseMeta(doc, snapshot);
        CertificationExamMeta meta = OfficialCertificationExamFormat.merge(null, url, parsed);
        return new FetchResult(snapshot, meta);
    }

    CertificationExamMeta parseMeta(Document doc, String snapshot) {
        String rulesText = standardExamText(snapshot);
        Integer duration = parseDurationMinutes(rulesText);
        Integer qMin = null;
        Integer qMax = null;
        Matcher range = QUESTION_RANGE_PATTERN.matcher(rulesText);
        if (range.find()) {
            qMin = Integer.parseInt(range.group(1));
            qMax = Integer.parseInt(range.group(2));
        } else {
            Matcher single = QUESTION_SINGLE_PATTERN.matcher(rulesText);
            if (single.find()) {
                int n = Integer.parseInt(single.group(1));
                qMin = n;
                qMax = n;
            }
        }

        List<String> domains = extractDomains(doc, snapshot);
        String formatNotes = null;
        String lower = rulesText.toLowerCase(Locale.ROOT);
        if (lower.contains("multiple select") || lower.contains("multiple-select")) {
            formatNotes = "multiple choice and multiple select questions";
        } else if (lower.contains("multiple choice")) {
            formatNotes = "multiple choice questions";
        }

        return CertificationExamMeta.builder()
                .durationMinutes(duration)
                .questionCountMin(qMin)
                .questionCountMax(qMax)
                .domains(domains)
                .formatNotes(formatNotes)
                .fetchedAt(IndiaTime.now())
                .build();
    }

    /** Standard-exam block only — GCP pages list a shorter renewal exam afterwards. */
    static String standardExamText(String snapshot) {
        if (!StringUtils.hasText(snapshot)) {
            return "";
        }
        Matcher cut = RENEWAL_CUT_PATTERN.matcher(snapshot);
        if (cut.find()) {
            return snapshot.substring(0, cut.start());
        }
        return snapshot;
    }

    static Integer parseDurationMinutes(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        Matcher hours = DURATION_HOURS_PATTERN.matcher(text);
        if (hours.find()) {
            return wordOrInt(hours.group(1)) * 60;
        }
        Matcher minutes = DURATION_MINUTES_PATTERN.matcher(text);
        if (minutes.find()) {
            return Integer.parseInt(minutes.group(1));
        }
        return null;
    }

    private static int wordOrInt(String raw) {
        String v = raw.toLowerCase(Locale.ROOT);
        return switch (v) {
            case "one" -> 1;
            case "two" -> 2;
            case "three" -> 3;
            default -> Integer.parseInt(v);
        };
    }

    private List<String> extractDomains(Document doc, String snapshot) {
        LinkedHashSet<String> domains = new LinkedHashSet<>();
        if (doc != null) {
            // Official guides label real domains "Section N: … (~X% of the exam)"; when present,
            // they are authoritative and generic bullets (marketing links) are ignored.
            for (Element el : doc.select("h1, h2, h3, h4, h5, li, p, strong, b")) {
                String t = el.ownText() != null && !el.ownText().isBlank() ? el.ownText().trim() : el.text().trim();
                if (t.length() <= 160 && SECTION_DOMAIN_PATTERN.matcher(t).find() && !looksLikeJunk(t)) {
                    domains.add(t);
                }
                if (domains.size() >= 12) {
                    break;
                }
            }
            if (!domains.isEmpty()) {
                return new ArrayList<>(domains);
            }
            Elements lists = doc.select("ul li, ol li");
            for (Element li : lists) {
                String t = li.text() != null ? li.text().trim() : "";
                if (looksLikeDomain(t)) {
                    domains.add(t);
                }
                if (domains.size() >= 12) {
                    break;
                }
            }
        }
        if (domains.isEmpty() && StringUtils.hasText(snapshot)) {
            // Fallback: lines that look like section titles after "assesses"
            String[] lines = snapshot.split("[\\r\\n]+|(?<=\\.)\\s+(?=[A-Z])");
            boolean capture = false;
            for (String line : lines) {
                String t = line.trim();
                if (t.toLowerCase(Locale.ROOT).contains("assesses your knowledge")
                        || t.toLowerCase(Locale.ROOT).contains("exam assesses")) {
                    capture = true;
                    continue;
                }
                if (capture && looksLikeDomain(t)) {
                    domains.add(t);
                }
                if (domains.size() >= 12) {
                    break;
                }
            }
        }
        return new ArrayList<>(domains);
    }

    private static boolean looksLikeDomain(String t) {
        if (!StringUtils.hasText(t) || t.length() < 12 || t.length() > 160) {
            return false;
        }
        String lower = t.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http") || looksLikeJunk(t)) {
            return false;
        }
        // Prefer topical phrases typical of GCP exam guides
        return lower.contains("google cloud")
                || lower.contains("cloud")
                || lower.contains("data")
                || lower.contains("security")
                || lower.contains("network")
                || lower.contains("machine learning")
                || lower.contains("devops")
                || lower.contains("ai ")
                || lower.contains("artificial intelligence")
                || lower.contains("infrastructure")
                || lower.contains("operations")
                || lower.contains("workspace")
                || lower.contains("database")
                || lower.contains("developer")
                || lower.contains("architect")
                || lower.contains("agentic");
    }

    /** Calls to action and page chrome that mention "cloud" but are not exam domains. */
    static boolean looksLikeJunk(String t) {
        String lower = t.toLowerCase(Locale.ROOT);
        for (String marker : JUNK_DOMAIN_MARKERS) {
            if (lower.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static void validateHttpUrl(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                throw new IllegalArgumentException("guidelinesUrl must be an http(s) URL");
            }
            if (!StringUtils.hasText(uri.getHost())) {
                throw new IllegalArgumentException("guidelinesUrl is missing a host");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("guidelinesUrl is not a valid URL");
        }
    }

    /** Snapshot older than this is refreshed on exam start. */
    public static boolean isStale(CertificationExamMeta meta, Duration maxAge) {
        if (meta == null || meta.getFetchedAt() == null) {
            return true;
        }
        return meta.getFetchedAt().isBefore(IndiaTime.now().minus(maxAge));
    }
}

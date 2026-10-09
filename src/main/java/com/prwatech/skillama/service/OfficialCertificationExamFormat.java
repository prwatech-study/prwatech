package com.prwatech.skillama.service;

import com.prwatech.skillama.model.CertificationExamMeta;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Official standard-exam length and question counts from each provider's guidelines page.
 * Used when the HTML parser misses "2 hours" / "~80 questions", or stored meta is the
 * generic 90 min / 50–60 default.
 */
public final class OfficialCertificationExamFormat {

    public record Format(int durationMinutes, int questionCountMin, int questionCountMax, String formatNotes) {}

    private static final String MCQ_MULTI = "multiple choice and multiple select questions";
    private static final String MCQ_ONLY = "multiple choice questions";

    /** Lookup by official URL path segment ({@code /certification/<key>}). */
    private static final Map<String, Format> BY_URL_KEY = new LinkedHashMap<>();
    /** Lookup by {@link GlobalCertificationExamService#nameKey(String)}. */
    private static final Map<String, Format> BY_NAME_KEY = new LinkedHashMap<>();

    static {
        // Foundational — https://cloud.google.com/learn/certification/cloud-digital-leader
        put("cloud-digital-leader", "Cloud Digital Leader",
                new Format(90, 50, 60, MCQ_MULTI));
        put("generative-ai-leader", "Generative AI Leader",
                new Format(90, 50, 60, MCQ_ONLY));

        // Associate
        put("cloud-engineer", "Associate Cloud Engineer",
                new Format(120, 50, 60, MCQ_MULTI));
        put("google-workspace-administrator", "Google Workspace Administrator",
                new Format(120, 50, 60, MCQ_MULTI));
        put("data-practitioner", "Data Practitioner",
                new Format(120, 50, 60, MCQ_MULTI));
        BY_NAME_KEY.put(nameKey("Associate Data Practitioner"), BY_URL_KEY.get("data-practitioner"));

        // Professional — most are 2 hours / 50–60; Data Engineer is 40–50; Agentic beta is ~80 / 3h
        put("cloud-architect", "Professional Cloud Architect",
                new Format(120, 50, 60,
                        MCQ_MULTI + "; 2 case studies (20-30% of the exam)"));
        put("cloud-database-engineer", "Professional Cloud Database Engineer",
                new Format(120, 50, 60, MCQ_MULTI));
        put("cloud-developer", "Professional Cloud Developer",
                new Format(120, 50, 60, MCQ_MULTI));
        put("data-engineer", "Professional Data Engineer",
                new Format(120, 40, 50, MCQ_MULTI));
        put("cloud-devops-engineer", "Professional Cloud DevOps Engineer",
                new Format(120, 50, 60, MCQ_MULTI));
        put("cloud-security-engineer", "Professional Cloud Security Engineer",
                new Format(120, 50, 60, MCQ_MULTI));
        put("cloud-network-engineer", "Professional Cloud Network Engineer",
                new Format(120, 50, 60, MCQ_MULTI));
        put("machine-learning-engineer", "Professional Machine Learning Engineer",
                new Format(120, 50, 60, MCQ_MULTI));
        put("security-operations-engineer", "Professional Security Operations Engineer",
                new Format(120, 50, 60, MCQ_MULTI));
        Format agentic = new Format(180, 80, 80,
                "~80 multiple choice questions (plus hands-on labs)");
        put("agentic-architect", "Professional Agentic Architect (Beta)", agentic);
        BY_NAME_KEY.put(nameKey("Professional Agentic Architect"), agentic);
    }

    private OfficialCertificationExamFormat() {}

    public static Optional<Format> lookup(String name, String guidelinesUrl) {
        String urlKey = urlKey(guidelinesUrl);
        if (urlKey != null && BY_URL_KEY.containsKey(urlKey)) {
            return Optional.of(BY_URL_KEY.get(urlKey));
        }
        if (StringUtils.hasText(name)) {
            String key = nameKey(name);
            if (BY_NAME_KEY.containsKey(key)) {
                return Optional.of(BY_NAME_KEY.get(key));
            }
            String stripped = key.replace(" (beta)", "").replace("(beta)", "").trim();
            if (BY_NAME_KEY.containsKey(stripped)) {
                return Optional.of(BY_NAME_KEY.get(stripped));
            }
        }
        return Optional.empty();
    }

    /**
     * Prefer values parsed from the standard-exam section. Fill gaps, replace the generic
     * 90 / 50–60 default when the official guide differs, and ignore renewal-exam sizes.
     */
    public static CertificationExamMeta merge(String name, String guidelinesUrl, CertificationExamMeta parsed) {
        Format official = lookup(name, guidelinesUrl).orElse(null);
        Integer duration = parsed != null ? parsed.getDurationMinutes() : null;
        Integer qMin = parsed != null ? parsed.getQuestionCountMin() : null;
        Integer qMax = parsed != null ? parsed.getQuestionCountMax() : null;
        String notes = parsed != null ? parsed.getFormatNotes() : null;

        if (official != null) {
            if (duration == null || looksLikeRenewalDuration(duration)
                    || isGenericDurationDefault(duration, official)) {
                duration = official.durationMinutes();
            }
            if (qMin == null || qMax == null || looksLikeRenewalCounts(qMin, qMax)
                    || isGenericCountDefault(qMin, qMax, official)) {
                qMin = official.questionCountMin();
                qMax = official.questionCountMax();
            }
            notes = mergeFormatNotes(notes, official.formatNotes());
        } else {
            if (duration == null) {
                duration = 90;
            }
            if (qMin == null) {
                qMin = 50;
            }
            if (qMax == null) {
                qMax = 60;
            }
            if (!StringUtils.hasText(notes)) {
                notes = MCQ_MULTI;
            }
        }

        return CertificationExamMeta.builder()
                .durationMinutes(duration)
                .questionCountMin(qMin)
                .questionCountMax(qMax)
                .domains(parsed != null && parsed.getDomains() != null
                        ? parsed.getDomains() : new ArrayList<>())
                .formatNotes(notes)
                .fetchedAt(parsed != null ? parsed.getFetchedAt() : null)
                .build();
    }

    public static boolean sameExamRules(CertificationExamMeta a, CertificationExamMeta b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return Objects.equals(a.getDurationMinutes(), b.getDurationMinutes())
                && Objects.equals(a.getQuestionCountMin(), b.getQuestionCountMin())
                && Objects.equals(a.getQuestionCountMax(), b.getQuestionCountMax());
    }

    static boolean looksLikeRenewalDuration(Integer minutes) {
        return minutes != null && (minutes == 45 || minutes == 60);
    }

    static boolean looksLikeRenewalCounts(Integer min, Integer max) {
        return min != null && max != null && min <= 25 && max <= 25;
    }

    static boolean isGenericDurationDefault(Integer duration, Format official) {
        return duration != null && duration == 90 && official.durationMinutes() != 90;
    }

    static boolean isGenericCountDefault(Integer min, Integer max, Format official) {
        return min != null && max != null && min == 50 && max == 60
                && (official.questionCountMin() != 50 || official.questionCountMax() != 60);
    }

    private static String mergeFormatNotes(String parsed, String official) {
        if (!StringUtils.hasText(parsed)) {
            return StringUtils.hasText(official) ? official : MCQ_MULTI;
        }
        if (!StringUtils.hasText(official)) {
            return parsed;
        }
        String lower = parsed.toLowerCase(Locale.ROOT);
        String extra = official;
        if (official.toLowerCase(Locale.ROOT).contains("case study") && !lower.contains("case")) {
            return parsed + "; " + extra;
        }
        if (official.toLowerCase(Locale.ROOT).contains("lab") && !lower.contains("lab")) {
            return parsed + "; " + extra;
        }
        return parsed;
    }

    static String urlKey(String guidelinesUrl) {
        if (!StringUtils.hasText(guidelinesUrl)) {
            return null;
        }
        try {
            String path = URI.create(guidelinesUrl.trim()).getPath();
            if (!StringUtils.hasText(path)) {
                return null;
            }
            String[] parts = path.split("/");
            for (int i = parts.length - 1; i >= 0; i--) {
                if (StringUtils.hasText(parts[i])) {
                    return parts[i].toLowerCase(Locale.ROOT);
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private static void put(String urlKey, String name, Format format) {
        BY_URL_KEY.put(urlKey, format);
        BY_NAME_KEY.put(nameKey(name), format);
    }

    private static String nameKey(String name) {
        return GlobalCertificationExamService.nameKey(name);
    }
}

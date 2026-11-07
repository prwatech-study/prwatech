package com.prwatech.skillama.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Learner features an event admin can turn on or off.
 * Corporate, org, and SSO codes are intentionally absent.
 */
public final class EventFeatureCatalog {

    public record Feature(String code, String label, String moduleName) {}

    public static final List<Feature> FEATURES = List.of(
            new Feature("ai_tutor", "AI Tutor", "Ai-Tutor"),
            new Feature("ai_mentor", "AI Mentor", null),
            new Feature("code_lab", "Code Lab", "Code Execution"),
            new Feature("debug_assistant", "Debug Assistant", "Debug"),
            new Feature("ai_exam", "AI Exams", null),
            new Feature("module_quiz", "Module Quizzes", null),
            new Feature("study_materials", "Study Materials", null),
            new Feature("learner_analytics", "Learner Analytics", null));

    private static final Map<String, String> MODULE_TO_CODE = Map.of(
            "ai-tutor", "ai_tutor",
            "code execution", "code_lab",
            "debug", "debug_assistant");

    private static final Set<String> CODES = FEATURES.stream()
            .map(Feature::code)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    private EventFeatureCatalog() {}

    public static boolean isEventFeature(String code) {
        return code != null && CODES.contains(code.trim().toLowerCase(Locale.ROOT));
    }

    public static String codeForModule(String moduleName) {
        if (moduleName == null) {
            return null;
        }
        return MODULE_TO_CODE.get(moduleName.trim().toLowerCase(Locale.ROOT));
    }

    public static List<String> modulesFor(Iterable<String> featureCodes) {
        List<String> modules = new java.util.ArrayList<>();
        if (featureCodes == null) {
            return modules;
        }
        for (Feature feature : FEATURES) {
            if (feature.moduleName() == null) {
                continue;
            }
            for (String code : featureCodes) {
                if (feature.code().equalsIgnoreCase(code)) {
                    modules.add(feature.moduleName());
                }
            }
        }
        return modules;
    }
}

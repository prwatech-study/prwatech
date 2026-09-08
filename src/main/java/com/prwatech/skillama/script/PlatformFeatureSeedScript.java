package com.prwatech.skillama.script;

import com.prwatech.skillama.model.PlatformFeature;
import com.prwatech.skillama.repository.PlatformFeatureRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PlatformFeatureSeedScript implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlatformFeatureSeedScript.class);

    private final PlatformFeatureRepository platformFeatureRepository;

    @Override
    public void run(String... args) {
        if (platformFeatureRepository.count() > 0) {
            return;
        }
        LOGGER.info("Seeding platform_features catalog...");
        platformFeatureRepository.saveAll(defaultFeatures());
    }

    private List<PlatformFeature> defaultFeatures() {
        return List.of(
                feature("ai_tutor", "AI Tutor", PlatformFeature.FeatureCategory.LMS, 10, null),
                feature("ai_mentor", "AI Mentor", PlatformFeature.FeatureCategory.AI, 20, null),
                feature("code_lab", "Code Lab", PlatformFeature.FeatureCategory.AI, 30, null),
                feature("debug_assistant", "Debug Assistant", PlatformFeature.FeatureCategory.AI, 40, null),
                feature("ai_exam", "AI Exams", PlatformFeature.FeatureCategory.LMS, 50, null),
                feature("module_quiz", "Module Quizzes", PlatformFeature.FeatureCategory.LMS, 60, null),
                feature("study_materials", "Study Materials", PlatformFeature.FeatureCategory.LMS, 70, null),
                feature("learner_analytics", "Learner Analytics", PlatformFeature.FeatureCategory.LMS, 80, null),
                feature("team_analytics", "Team Analytics", PlatformFeature.FeatureCategory.CORPORATE, 90, List.of("org_hierarchy")),
                feature("org_hierarchy", "Org Hierarchy", PlatformFeature.FeatureCategory.CORPORATE, 100, null),
                feature("org_user_management", "Org User Management", PlatformFeature.FeatureCategory.CORPORATE, 110, null),
                feature("csv_user_import", "CSV User Import", PlatformFeature.FeatureCategory.CORPORATE, 120, null),
                feature("white_label_branding", "White-label Branding", PlatformFeature.FeatureCategory.BRANDING, 130, null),
                feature("branded_certificates", "Branded Certificates", PlatformFeature.FeatureCategory.BRANDING, 140, null),
                feature("sso_google_workspace", "Google Workspace SSO", PlatformFeature.FeatureCategory.SECURITY, 150, null),
                feature("sso_microsoft_entra", "Microsoft Entra SSO", PlatformFeature.FeatureCategory.SECURITY, 160, null),
                feature("email_password_auth", "Email Password Auth", PlatformFeature.FeatureCategory.SECURITY, 170, null),
                limitFeature("max_seats", "Max Seats", 180),
                feature("time_wallet", "Time Wallet", PlatformFeature.FeatureCategory.LIMIT, 190, null),
                feature("custom_subdomain", "Custom Subdomain", PlatformFeature.FeatureCategory.BRANDING, 200, null),
                feature("custom_domain", "Custom Domain", PlatformFeature.FeatureCategory.BRANDING, 210, null));
    }

    private PlatformFeature feature(
            String code, String name, PlatformFeature.FeatureCategory category, int sort, List<String> dependsOn) {
        return PlatformFeature.builder()
                .code(code)
                .name(name)
                .description(name)
                .category(category)
                .valueType(PlatformFeature.ValueType.BOOLEAN)
                .sortOrder(sort)
                .dependsOn(dependsOn != null ? dependsOn : List.of())
                .active(true)
                .build();
    }

    private PlatformFeature limitFeature(String code, String name, int sort) {
        return PlatformFeature.builder()
                .code(code)
                .name(name)
                .description(name)
                .category(PlatformFeature.FeatureCategory.LIMIT)
                .valueType(PlatformFeature.ValueType.INTEGER)
                .sortOrder(sort)
                .active(true)
                .build();
    }
}

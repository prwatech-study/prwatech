package com.prwatech.skillama.script;

import com.prwatech.skillama.model.PlatformFeature;
import com.prwatech.skillama.repository.PlatformFeatureRepository;
import com.prwatech.skillama.service.PlatformFeatureRolloutService;
import com.prwatech.skillama.util.IndiaTime;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PlatformFeatureSeedScript implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlatformFeatureSeedScript.class);

    private final PlatformFeatureRepository platformFeatureRepository;

    @Override
    public void run(String... args) {
        int added = 0;
        for (PlatformFeature feature : defaultFeatures()) {
            if (platformFeatureRepository.findByCode(feature.getCode()).isEmpty()) {
                platformFeatureRepository.save(feature);
                added++;
            }
        }
        if (added > 0) {
            LOGGER.info("Seeded {} platform_features catalog entries", added);
        }

        int backfilled = backfillRolloutFields();
        if (backfilled > 0) {
            LOGGER.info("Backfilled rollout fields on {} platform_features", backfilled);
        }
    }

    /**
     * One-time defaults for docs created before rolloutStatus existed.
     * Interview tools → UPCOMING; other learner tools → LIVE with aged liveAt; rest → LIVE.
     */
    private int backfillRolloutFields() {
        LocalDateTime agedLiveAt = IndiaTime.now().minusDays(PlatformFeatureRolloutService.NEW_BADGE_DAYS + 1);
        int updated = 0;
        for (PlatformFeature existing : platformFeatureRepository.findAll()) {
            boolean dirty = false;
            if (existing.getRolloutStatus() == null) {
                if (PlatformFeatureRolloutService.isInitialUpcoming(existing.getCode())) {
                    existing.setRolloutStatus(PlatformFeature.RolloutStatus.UPCOMING);
                    existing.setLiveAt(null);
                } else {
                    existing.setRolloutStatus(PlatformFeature.RolloutStatus.LIVE);
                    if (existing.getLiveAt() == null) {
                        existing.setLiveAt(agedLiveAt);
                    }
                }
                dirty = true;
            }
            boolean shouldBeLearnerVisible = PlatformFeatureRolloutService.isLearnerCatalogCode(existing.getCode());
            if (existing.isLearnerVisible() != shouldBeLearnerVisible) {
                // Only flip false→true for catalog codes; never force-hide Owner overrides on non-catalog.
                if (shouldBeLearnerVisible && !existing.isLearnerVisible()) {
                    existing.setLearnerVisible(true);
                    dirty = true;
                }
            }
            if (dirty) {
                platformFeatureRepository.save(existing);
                updated++;
            }
        }
        return updated;
    }

    private List<PlatformFeature> defaultFeatures() {
        LocalDateTime agedLiveAt = IndiaTime.now().minusDays(PlatformFeatureRolloutService.NEW_BADGE_DAYS + 1);
        return List.of(
                learnerLive("ai_tutor", "AI Tutor", PlatformFeature.FeatureCategory.LMS, 10, null, agedLiveAt),
                learnerLive("ai_mentor", "AI Mentor", PlatformFeature.FeatureCategory.AI, 20, null, agedLiveAt),
                learnerLive("code_lab", "Code Lab", PlatformFeature.FeatureCategory.AI, 30, null, agedLiveAt),
                learnerLive(
                        "debug_assistant",
                        "Debug Assistant",
                        PlatformFeature.FeatureCategory.AI,
                        40,
                        null,
                        agedLiveAt),
                learnerLive("ai_exam", "AI Exams", PlatformFeature.FeatureCategory.LMS, 50, null, agedLiveAt),
                learnerUpcoming("ai_interview", "AI Interview", PlatformFeature.FeatureCategory.LMS, 55),
                learnerUpcoming("ai_mock_interview", "AI Mock Interview", PlatformFeature.FeatureCategory.LMS, 56),
                feature("module_quiz", "Module Quizzes", PlatformFeature.FeatureCategory.LMS, 60, null, agedLiveAt),
                feature("study_materials", "Study Materials", PlatformFeature.FeatureCategory.LMS, 70, null, agedLiveAt),
                learnerLive(
                        "learner_analytics",
                        "Learner Analytics",
                        PlatformFeature.FeatureCategory.LMS,
                        80,
                        null,
                        agedLiveAt),
                feature(
                        "team_analytics",
                        "Team Analytics",
                        PlatformFeature.FeatureCategory.CORPORATE,
                        90,
                        List.of("org_hierarchy"),
                        agedLiveAt),
                feature(
                        "org_hierarchy",
                        "Org Hierarchy",
                        PlatformFeature.FeatureCategory.CORPORATE,
                        100,
                        null,
                        agedLiveAt),
                feature(
                        "org_user_management",
                        "Org User Management",
                        PlatformFeature.FeatureCategory.CORPORATE,
                        110,
                        null,
                        agedLiveAt),
                feature(
                        "csv_user_import",
                        "CSV User Import",
                        PlatformFeature.FeatureCategory.CORPORATE,
                        120,
                        null,
                        agedLiveAt),
                feature(
                        "white_label_branding",
                        "White-label Branding",
                        PlatformFeature.FeatureCategory.BRANDING,
                        130,
                        null,
                        agedLiveAt),
                feature(
                        "branded_certificates",
                        "Branded Certificates",
                        PlatformFeature.FeatureCategory.BRANDING,
                        140,
                        null,
                        agedLiveAt),
                feature(
                        "sso_google_workspace",
                        "Google Workspace SSO",
                        PlatformFeature.FeatureCategory.SECURITY,
                        150,
                        null,
                        agedLiveAt),
                feature(
                        "sso_microsoft_entra",
                        "Microsoft Entra SSO",
                        PlatformFeature.FeatureCategory.SECURITY,
                        160,
                        null,
                        agedLiveAt),
                feature(
                        "email_password_auth",
                        "Email Password Auth",
                        PlatformFeature.FeatureCategory.SECURITY,
                        170,
                        null,
                        agedLiveAt),
                limitFeature("max_seats", "Max Seats", 180, agedLiveAt),
                feature("time_wallet", "Time Wallet", PlatformFeature.FeatureCategory.LIMIT, 190, null, agedLiveAt),
                feature(
                        "custom_subdomain",
                        "Custom Subdomain",
                        PlatformFeature.FeatureCategory.BRANDING,
                        200,
                        null,
                        agedLiveAt),
                feature(
                        "custom_domain",
                        "Custom Domain",
                        PlatformFeature.FeatureCategory.BRANDING,
                        210,
                        null,
                        agedLiveAt));
    }

    private PlatformFeature learnerLive(
            String code,
            String name,
            PlatformFeature.FeatureCategory category,
            int sort,
            List<String> dependsOn,
            LocalDateTime liveAt) {
        return base(code, name, category, sort, dependsOn)
                .learnerVisible(true)
                .rolloutStatus(PlatformFeature.RolloutStatus.LIVE)
                .liveAt(liveAt)
                .build();
    }

    private PlatformFeature learnerUpcoming(
            String code, String name, PlatformFeature.FeatureCategory category, int sort) {
        return base(code, name, category, sort, null)
                .learnerVisible(true)
                .rolloutStatus(PlatformFeature.RolloutStatus.UPCOMING)
                .liveAt(null)
                .build();
    }

    private PlatformFeature feature(
            String code,
            String name,
            PlatformFeature.FeatureCategory category,
            int sort,
            List<String> dependsOn,
            LocalDateTime liveAt) {
        return base(code, name, category, sort, dependsOn)
                .learnerVisible(false)
                .rolloutStatus(PlatformFeature.RolloutStatus.LIVE)
                .liveAt(liveAt)
                .build();
    }

    private PlatformFeature limitFeature(String code, String name, int sort, LocalDateTime liveAt) {
        return PlatformFeature.builder()
                .code(code)
                .name(name)
                .description(name)
                .category(PlatformFeature.FeatureCategory.LIMIT)
                .valueType(PlatformFeature.ValueType.INTEGER)
                .sortOrder(sort)
                .active(true)
                .learnerVisible(false)
                .rolloutStatus(PlatformFeature.RolloutStatus.LIVE)
                .liveAt(liveAt)
                .build();
    }

    private PlatformFeature.PlatformFeatureBuilder base(
            String code,
            String name,
            PlatformFeature.FeatureCategory category,
            int sort,
            List<String> dependsOn) {
        return PlatformFeature.builder()
                .code(code)
                .name(name)
                .description(name)
                .category(category)
                .valueType(PlatformFeature.ValueType.BOOLEAN)
                .sortOrder(sort)
                .dependsOn(dependsOn != null ? dependsOn : List.of())
                .active(true);
    }
}

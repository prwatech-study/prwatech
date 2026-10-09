package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.PlatformFeatureRolloutDTO;
import com.prwatech.skillama.dto.UpdateFeatureRolloutDTO;
import com.prwatech.skillama.exception.FeatureNotLiveException;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.PlatformFeature;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.PlatformFeatureRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.util.IndiaTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PlatformFeatureRolloutService {

    public static final int NEW_BADGE_DAYS = 90;

    public static final String AI_TUTOR = "ai_tutor";
    public static final String AI_MENTOR = "ai_mentor";
    public static final String CODE_LAB = "code_lab";
    public static final String DEBUG_ASSISTANT = "debug_assistant";
    public static final String AI_EXAM = "ai_exam";
    public static final String AI_INTERVIEW = "ai_interview";
    public static final String AI_MOCK_INTERVIEW = "ai_mock_interview";
    public static final String LEARNER_ANALYTICS = "learner_analytics";

    private static final Set<String> LEARNER_CATALOG_CODES = Set.of(
            AI_TUTOR,
            AI_MENTOR,
            CODE_LAB,
            DEBUG_ASSISTANT,
            AI_EXAM,
            AI_INTERVIEW,
            AI_MOCK_INTERVIEW,
            LEARNER_ANALYTICS);

    private static final Set<String> INITIAL_UPCOMING = Set.of(AI_INTERVIEW, AI_MOCK_INTERVIEW);

    private final PlatformFeatureRepository platformFeatureRepository;
    private final SkillamaUserRepository userRepository;

    public List<PlatformFeatureRolloutDTO> listLearnerCatalog() {
        return platformFeatureRepository.findByLearnerVisibleTrueAndActiveTrueOrderBySortOrderAsc().stream()
                .map(this::toDto)
                .toList();
    }

    public List<PlatformFeatureRolloutDTO> listAllForOwner() {
        return platformFeatureRepository.findByActiveTrueOrderBySortOrderAsc().stream()
                .map(this::toDto)
                .toList();
    }

    public PlatformFeatureRolloutDTO updateRollout(String code, UpdateFeatureRolloutDTO body, String ownerUserId) {
        if (body == null || body.getRolloutStatus() == null) {
            throw new IllegalArgumentException("rolloutStatus is required");
        }
        PlatformFeature feature = platformFeatureRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Feature not found: " + code));

        PlatformFeature.RolloutStatus next = body.getRolloutStatus();
        PlatformFeature.RolloutStatus previous = feature.getEffectiveRolloutStatus();
        feature.setRolloutStatus(next);
        if (next == PlatformFeature.RolloutStatus.LIVE) {
            if (previous != PlatformFeature.RolloutStatus.LIVE || feature.getLiveAt() == null) {
                feature.setLiveAt(IndiaTime.now());
            }
        } else {
            feature.setLiveAt(null);
        }
        return toDto(platformFeatureRepository.save(feature));
    }

    /**
     * LIVE → allow everyone. UPCOMING/HIDDEN → allow only ADMIN, OWNER, TESTER.
     * Missing catalog entry is treated as LIVE (legacy codes).
     */
    public void assertAccessible(String featureCode, String userId) {
        if (featureCode == null || featureCode.isBlank()) {
            return;
        }
        PlatformFeature feature = platformFeatureRepository.findByCode(featureCode).orElse(null);
        if (feature == null) {
            return;
        }
        PlatformFeature.RolloutStatus status = feature.getEffectiveRolloutStatus();
        if (status == PlatformFeature.RolloutStatus.LIVE) {
            return;
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new FeatureNotLiveException(
                        featureCode, "This feature is not available yet."));
        if (isStaff(user)) {
            return;
        }
        throw new FeatureNotLiveException(
                featureCode,
                "This feature is not available yet. Check back soon.");
    }

    /**
     * Anonymous/guest paths (invite preview/join): only LIVE is allowed.
     * In-flight session tokens are not checked here so active interviews can finish.
     */
    public void assertPubliclyLive(String featureCode) {
        if (featureCode == null || featureCode.isBlank()) {
            return;
        }
        PlatformFeature feature = platformFeatureRepository.findByCode(featureCode).orElse(null);
        if (feature == null) {
            return;
        }
        if (feature.getEffectiveRolloutStatus() == PlatformFeature.RolloutStatus.LIVE) {
            return;
        }
        throw new FeatureNotLiveException(
                featureCode,
                "This feature is not available yet. Check back soon.");
    }

    /** True when the feature is LIVE (missing catalog entry counts as LIVE). */
    public boolean isLive(String featureCode) {
        if (featureCode == null || featureCode.isBlank()) {
            return true;
        }
        PlatformFeature feature = platformFeatureRepository.findByCode(featureCode).orElse(null);
        if (feature == null) {
            return true;
        }
        return feature.getEffectiveRolloutStatus() == PlatformFeature.RolloutStatus.LIVE;
    }

    public boolean isNew(PlatformFeature feature) {
        if (feature == null || feature.getEffectiveRolloutStatus() != PlatformFeature.RolloutStatus.LIVE) {
            return false;
        }
        LocalDateTime liveAt = feature.getLiveAt();
        if (liveAt == null) {
            return false;
        }
        return liveAt.isAfter(IndiaTime.now().minusDays(NEW_BADGE_DAYS));
    }

    public static boolean isLearnerCatalogCode(String code) {
        return code != null && LEARNER_CATALOG_CODES.contains(code);
    }

    public static boolean isInitialUpcoming(String code) {
        return code != null && INITIAL_UPCOMING.contains(code);
    }

    public static Set<String> learnerCatalogCodes() {
        return LEARNER_CATALOG_CODES;
    }

    private boolean isStaff(User user) {
        User.UserRole role = user.getEffectiveRole();
        return role == User.UserRole.ADMIN
                || role == User.UserRole.OWNER
                || role == User.UserRole.TESTER;
    }

    private PlatformFeatureRolloutDTO toDto(PlatformFeature feature) {
        return PlatformFeatureRolloutDTO.builder()
                .code(feature.getCode())
                .name(feature.getName())
                .description(feature.getDescription())
                .category(feature.getCategory())
                .rolloutStatus(feature.getEffectiveRolloutStatus())
                .liveAt(feature.getLiveAt())
                .learnerVisible(feature.isLearnerVisible())
                .newBadge(isNew(feature))
                .sortOrder(feature.getSortOrder())
                .active(feature.isActive())
                .build();
    }
}

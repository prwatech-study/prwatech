package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.PlatformFeatureRolloutDTO;
import com.prwatech.skillama.dto.UpdateFeatureRolloutDTO;
import com.prwatech.skillama.exception.FeatureNotLiveException;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.PlatformFeature;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.AiInterviewScheduleRepository;
import com.prwatech.skillama.repository.PlatformFeatureRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.util.IndiaTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlatformFeatureRolloutService {

    public static final int NEW_BADGE_DAYS = 90;

    public static final String AI_TUTOR = "ai_tutor";
    public static final String AI_MENTOR = "ai_mentor";
    public static final String CODE_LAB = "code_lab";
    public static final String DEBUG_ASSISTANT = "debug_assistant";
    public static final String AI_EXAM = "ai_exam";
    /** Nested under AI Exam — Global certifications mode (not a separate home tile). */
    public static final String GLOBAL_CERTIFICATION = "global_certification";
    public static final String AI_INTERVIEW = "ai_interview";
    public static final String AI_MOCK_INTERVIEW = "ai_mock_interview";
    public static final String LEARNER_ANALYTICS = "learner_analytics";

    private static final Set<String> LEARNER_CATALOG_CODES = Set.of(
            AI_TUTOR,
            AI_MENTOR,
            CODE_LAB,
            DEBUG_ASSISTANT,
            AI_EXAM,
            GLOBAL_CERTIFICATION,
            AI_INTERVIEW,
            AI_MOCK_INTERVIEW,
            LEARNER_ANALYTICS);

    /** Interview tools stay UPCOMING until Owner activates LIVE after team testing. */
    private static final Set<String> INITIAL_UPCOMING = Set.of(
            AI_INTERVIEW,
            AI_MOCK_INTERVIEW);

    private final PlatformFeatureRepository platformFeatureRepository;
    private final SkillamaUserRepository userRepository;
    private final AiInterviewScheduleRepository aiInterviewScheduleRepository;

    public List<PlatformFeatureRolloutDTO> listLearnerCatalog() {
        return listLearnerCatalog(null);
    }

    /**
     * Learner catalog. When {@code viewerUserId} is set, effective status may be LIVE for
     * that user only (Mock pilots / AI Interview assignees) while global stays UPCOMING.
     */
    public List<PlatformFeatureRolloutDTO> listLearnerCatalog(String viewerUserId) {
        return platformFeatureRepository.findByLearnerVisibleTrueAndActiveTrueOrderBySortOrderAsc().stream()
                .map(f -> toLearnerDto(f, viewerUserId))
                .toList();
    }

    public List<PlatformFeatureRolloutDTO> listAllForOwner() {
        return platformFeatureRepository.findByActiveTrueOrderBySortOrderAsc().stream()
                .map(this::toOwnerDto)
                .toList();
    }

    public PlatformFeatureRolloutDTO updateRollout(String code, UpdateFeatureRolloutDTO body, String ownerUserId) {
        if (body == null
                || (body.getRolloutStatus() == null && body.getPilotUserIds() == null)) {
            throw new IllegalArgumentException("rolloutStatus or pilotUserIds is required");
        }
        PlatformFeature feature = platformFeatureRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Feature not found: " + code));

        if (body.getRolloutStatus() != null) {
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
        }

        if (body.getPilotUserIds() != null) {
            feature.setPilotUserIds(normalizePilotUserIds(body.getPilotUserIds()));
        }

        return toOwnerDto(platformFeatureRepository.save(feature));
    }

    /**
     * LIVE → allow everyone.
     * UPCOMING/HIDDEN → staff, pilot users, or (AI Interview only) assigned candidates.
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
        if (isPilotUser(feature, userId)) {
            return;
        }
        if (AI_INTERVIEW.equals(featureCode) && hasAssignedInterview(user)) {
            return;
        }
        throw new FeatureNotLiveException(
                featureCode,
                "This feature is not available yet. Check back soon.");
    }

    /**
     * Anonymous/guest paths (invite preview/join): LIVE globally, or a valid invite token
     * (assignment unlocks the guest path without requiring platform-wide LIVE).
     */
    public void assertPubliclyLive(String featureCode) {
        assertPubliclyLive(featureCode, null);
    }

    public void assertPubliclyLive(String featureCode, String inviteToken) {
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
        if (AI_INTERVIEW.equals(featureCode)
                && inviteToken != null
                && !inviteToken.isBlank()
                && aiInterviewScheduleRepository.existsByInviteToken(inviteToken.trim())) {
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

    private boolean isPilotUser(PlatformFeature feature, String userId) {
        if (feature == null || userId == null || userId.isBlank()) {
            return false;
        }
        List<String> pilots = feature.getPilotUserIds();
        return pilots != null && pilots.contains(userId);
    }

    private boolean hasAssignedInterview(User user) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            return false;
        }
        return aiInterviewScheduleRepository.existsByCandidateEmail(normalizeEmail(user.getEmail()));
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static List<String> normalizePilotUserIds(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return new ArrayList<>();
        }
        return raw.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private PlatformFeatureRolloutDTO toLearnerDto(PlatformFeature feature, String viewerUserId) {
        PlatformFeature.RolloutStatus status = feature.getEffectiveRolloutStatus();
        boolean newBadge = isNew(feature);

        if (viewerUserId != null
                && !viewerUserId.isBlank()
                && status != PlatformFeature.RolloutStatus.LIVE) {
            if (isPilotUser(feature, viewerUserId)
                    || (AI_INTERVIEW.equals(feature.getCode())
                            && userRepository.findById(viewerUserId)
                                    .map(this::hasAssignedInterview)
                                    .orElse(false))) {
                status = PlatformFeature.RolloutStatus.LIVE;
                newBadge = false;
            }
        }

        return PlatformFeatureRolloutDTO.builder()
                .code(feature.getCode())
                .name(feature.getName())
                .description(feature.getDescription())
                .category(feature.getCategory())
                .rolloutStatus(status)
                .liveAt(status == PlatformFeature.RolloutStatus.LIVE ? feature.getLiveAt() : null)
                .learnerVisible(feature.isLearnerVisible())
                .newBadge(newBadge)
                .sortOrder(feature.getSortOrder())
                .active(feature.isActive())
                .build();
    }

    private PlatformFeatureRolloutDTO toOwnerDto(PlatformFeature feature) {
        List<String> pilots = feature.getPilotUserIds();
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
                .pilotUserIds(pilots != null ? new ArrayList<>(pilots) : new ArrayList<>())
                .build();
    }
}

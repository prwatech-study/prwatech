package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.CompleteLectureRequestDTO;
import com.prwatech.skillama.dto.ReconcileProgressRequestDTO;
import com.prwatech.skillama.dto.ReconcileProgressResultDTO;
import com.prwatech.skillama.model.CourseCurriculum;
import com.prwatech.skillama.model.UserCourseProgress;
import com.prwatech.skillama.model.UserLectureProgress;
import com.prwatech.skillama.model.UserProfile;
import com.prwatech.skillama.repository.CourseCurriculumRepository;
import com.prwatech.skillama.repository.UserCourseProgressRepository;
import com.prwatech.skillama.repository.UserLectureProgressRepository;
import com.prwatech.skillama.repository.UserProfileRepository;
import com.prwatech.skillama.util.IndiaTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Merges legacy/dashboard progress into the profiling store (UserProfile) so LMS
 * access-control and locks match what learners already completed.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProgressReconciliationService {

    private final UserProfileService userProfileService;
    private final UserProfileRepository userProfileRepository;
    private final UserLectureProgressRepository lectureProgressRepository;
    private final UserCourseProgressRepository courseProgressRepository;
    private final CourseCurriculumRepository curriculumRepository;
    private final UserCourseAccessService userCourseAccessService;

    @Transactional
    public ReconcileProgressResultDTO reconcileForUser(String userId, ReconcileProgressRequestDTO request) {
        return reconcileForUser(userId, request, false);
    }

    @Transactional
    public ReconcileProgressResultDTO reconcileForUserAsAdmin(String userId, ReconcileProgressRequestDTO request) {
        return reconcileForUser(userId, request, true);
    }

    private ReconcileProgressResultDTO reconcileForUser(
            String userId,
            ReconcileProgressRequestDTO request,
            boolean adminOverride) {
        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("userId is required");
        }
        if (request == null || !StringUtils.hasText(request.getCourseId())) {
            throw new IllegalArgumentException("courseId is required");
        }

        String courseId = request.getCourseId().trim();
        if (!adminOverride) {
            userCourseAccessService.assertCanAccessCourse(userId, courseId);
        }

        List<LectureRef> ordered = collectEnabledLectures(courseId);
        Set<String> targetLabels = collectTargetCompletedLabels(userId, courseId, request, ordered);

        int synced = 0;
        for (String label : targetLabels) {
            if (!isInOrderedCurriculum(label, ordered)) {
                continue;
            }
            if (isCompletedInProfile(userId, label, courseId)) {
                continue;
            }
            LectureRef ref = findLectureRef(label, ordered);
            try {
                CompleteLectureRequestDTO complete = CompleteLectureRequestDTO.builder()
                        .lectureLabel(label)
                        .courseId(courseId)
                        .moduleName(ref != null ? ref.moduleName : "Unknown Module")
                        .timeSpent(60)
                        .completionPercentage(100)
                        .completedAt(IndiaTime.now())
                        .build();
                userProfileService.completeLecture(null, userId, complete);
                synced++;
            } catch (Exception e) {
                log.warn("Progress reconcile skipped lecture {} for user {}: {}", label, userId, e.getMessage());
            }
        }

        int totalCompleted = countProfileCompletedForCourse(userId, courseId);
        int totalLectures = ordered.size();
        int percent = totalLectures > 0
                ? (int) Math.round((totalCompleted * 100.0) / totalLectures)
                : 0;

        return ReconcileProgressResultDTO.builder()
                .courseId(courseId)
                .syncedLectures(synced)
                .totalCompletedLectures(totalCompleted)
                .completionPercentage(percent)
                .build();
    }

    private Set<String> collectTargetCompletedLabels(
            String userId,
            String courseId,
            ReconcileProgressRequestDTO request,
            List<LectureRef> ordered) {
        Set<String> labels = new LinkedHashSet<>();

        userProfileRepository.findByUserId(userId).ifPresent(profile -> profile.getCompletedLectures().stream()
                .filter(cl -> courseId.equals(cl.getCourseId()) && StringUtils.hasText(cl.getLectureLabel()))
                .map(UserProfile.CompletedLecture::getLectureLabel)
                .forEach(labels::add));

        lectureProgressRepository.findByUserIdAndCourseId(userId, courseId).stream()
                .filter(lp -> Boolean.TRUE.equals(lp.getCompleted()))
                .map(UserLectureProgress::getLectureId)
                .filter(StringUtils::hasText)
                .forEach(labels::add);

        if (request.getClientCompletedLabels() != null) {
            request.getClientCompletedLabels().stream()
                    .filter(StringUtils::hasText)
                    .forEach(labels::add);
        }

        courseProgressRepository.findByUserIdAndCourseId(userId, courseId).ifPresent(aggregate -> {
            int aggregateCompleted = aggregate.getCompletedLectures() != null
                    ? aggregate.getCompletedLectures()
                    : 0;
            if (aggregateCompleted > labels.size() && !ordered.isEmpty()) {
                for (int i = 0; i < Math.min(aggregateCompleted, ordered.size()); i++) {
                    labels.add(ordered.get(i).label);
                }
            }
            Integer progressPercent = aggregate.getProgress();
            if (progressPercent != null && progressPercent > 0 && !ordered.isEmpty()) {
                int fromPercent = (int) Math.round(ordered.size() * (progressPercent / 100.0));
                for (int i = 0; i < Math.min(fromPercent, ordered.size()); i++) {
                    labels.add(ordered.get(i).label);
                }
            }
        });

        return labels;
    }

    private boolean isCompletedInProfile(String userId, String lectureLabel, String courseId) {
        return userProfileRepository.findByUserId(userId)
                .map(profile -> profile.getCompletedLectures().stream()
                        .anyMatch(cl -> courseId.equals(cl.getCourseId())
                                && lectureLabel.equals(cl.getLectureLabel())))
                .orElse(false);
    }

    private int countProfileCompletedForCourse(String userId, String courseId) {
        return (int) userProfileRepository.findByUserId(userId)
                .map(profile -> profile.getCompletedLectures().stream()
                        .filter(cl -> courseId.equals(cl.getCourseId()))
                        .map(UserProfile.CompletedLecture::getLectureLabel)
                        .distinct()
                        .count())
                .orElse(0L);
    }

    private List<LectureRef> collectEnabledLectures(String courseId) {
        List<CourseCurriculum> modules = curriculumRepository.findByCourseIdOrderByOrderAsc(courseId);
        List<LectureRef> out = new ArrayList<>();
        for (CourseCurriculum module : modules) {
            if (module.getSubmodules() == null) {
                continue;
            }
            for (CourseCurriculum.Submodule sub : module.getSubmodules()) {
                if (!CourseService.isSubmoduleEnabled(sub) || !StringUtils.hasText(sub.getLabel())) {
                    continue;
                }
                out.add(new LectureRef(sub.getLabel(), module.getModuleName()));
            }
        }
        return out;
    }

    private static boolean isInOrderedCurriculum(String label, List<LectureRef> ordered) {
        return ordered.stream().anyMatch(r -> r.label.equals(label));
    }

    private static LectureRef findLectureRef(String label, List<LectureRef> ordered) {
        return ordered.stream().filter(r -> r.label.equals(label)).findFirst().orElse(null);
    }

    private static final class LectureRef {
        final String label;
        final String moduleName;

        LectureRef(String label, String moduleName) {
            this.label = label;
            this.moduleName = moduleName;
        }
    }
}

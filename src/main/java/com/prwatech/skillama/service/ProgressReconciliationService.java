package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.BulkReconcileProgressResultDTO;
import com.prwatech.skillama.dto.CompleteLectureRequestDTO;
import com.prwatech.skillama.dto.ReconcileProgressRequestDTO;
import com.prwatech.skillama.dto.ReconcileProgressResultDTO;
import com.prwatech.skillama.model.UserCourseEnrollment;
import com.prwatech.skillama.model.CourseCurriculum;
import com.prwatech.skillama.model.UserCourseProgress;
import com.prwatech.skillama.model.UserLectureProgress;
import com.prwatech.skillama.model.UserProfile;
import com.prwatech.skillama.repository.CourseCurriculumRepository;
import com.prwatech.skillama.repository.UserCourseEnrollmentRepository;
import com.prwatech.skillama.repository.UserCourseProgressRepository;
import com.prwatech.skillama.repository.UserLectureProgressRepository;
import com.prwatech.skillama.repository.UserProfileRepository;
import com.prwatech.skillama.util.IndiaTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
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
    private final UserCourseEnrollmentRepository enrollmentRepository;

    private ProgressReconciliationService self;

    @Autowired
    @Lazy
    void setSelf(ProgressReconciliationService self) {
        this.self = self;
    }

  private static final int MAX_FAILURE_SAMPLES = 25;

    @Transactional
    public ReconcileProgressResultDTO reconcileForUser(String userId, ReconcileProgressRequestDTO request) {
        return reconcileForUser(userId, request, false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReconcileProgressResultDTO reconcileForUserAsAdmin(String userId, ReconcileProgressRequestDTO request) {
        return reconcileForUser(userId, request, true);
    }

    /**
     * OWNER maintenance: reconcile progress for every active enrollment (all learners × assigned courses).
     * Each enrollment is reconciled independently so partial failures do not roll back the whole run.
     */
    public BulkReconcileProgressResultDTO reconcileAllActiveEnrollments(boolean dryRun) {
        List<UserCourseEnrollment> enrollments = enrollmentRepository.findByStatus(
                UserCourseEnrollment.EnrollmentStatus.ACTIVE);
        return reconcileEnrollments(enrollments, dryRun);
    }

    /**
     * Reconcile progress for every active enrollment of a single learner (all assigned courses).
     */
    public BulkReconcileProgressResultDTO reconcileAllCoursesForUser(String userId, boolean dryRun) {
        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("userId is required");
        }
        List<UserCourseEnrollment> enrollments = enrollmentRepository.findByUserIdAndStatus(
                userId.trim(), UserCourseEnrollment.EnrollmentStatus.ACTIVE);
        return reconcileEnrollments(enrollments, dryRun);
    }

    private BulkReconcileProgressResultDTO reconcileEnrollments(
            List<UserCourseEnrollment> enrollments,
            boolean dryRun) {
        Set<String> userIds = new HashSet<>();
        Set<String> courseIds = new HashSet<>();
        int lecturesSynced = 0;
        int failures = 0;
        List<String> failureSamples = new ArrayList<>();

        for (UserCourseEnrollment enrollment : enrollments) {
            if (enrollment == null
                    || !StringUtils.hasText(enrollment.getUserId())
                    || !StringUtils.hasText(enrollment.getCourseId())) {
                continue;
            }
            userIds.add(enrollment.getUserId());
            courseIds.add(enrollment.getCourseId());

            if (dryRun) {
                continue;
            }

            try {
                ReconcileProgressRequestDTO request = ReconcileProgressRequestDTO.builder()
                        .courseId(enrollment.getCourseId())
                        .build();
                ReconcileProgressResultDTO result = self.reconcileForUserAsAdmin(enrollment.getUserId(), request);
                lecturesSynced += result.getSyncedLectures();
            } catch (Exception e) {
                failures++;
                if (failureSamples.size() < MAX_FAILURE_SAMPLES) {
                    failureSamples.add(enrollment.getUserId() + " / " + enrollment.getCourseId()
                            + ": " + e.getMessage());
                }
                log.warn("Bulk progress reconcile failed for user {} course {}: {}",
                        enrollment.getUserId(), enrollment.getCourseId(), e.getMessage());
            }
        }

        return BulkReconcileProgressResultDTO.builder()
                .dryRun(dryRun)
                .enrollmentsProcessed(enrollments.size())
                .uniqueUsers(userIds.size())
                .uniqueCourses(courseIds.size())
                .totalLecturesSynced(lecturesSynced)
                .failures(failures)
                .failureSamples(failureSamples)
                .build();
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

        boolean hasPendingSync = false;
        for (String label : targetLabels) {
            if (!isInOrderedCurriculum(label, ordered)) {
                continue;
            }
            if (isCompletedInProfile(userId, label, courseId)) {
                continue;
            }
            hasPendingSync = true;
            break;
        }

        int synced = 0;
        if (hasPendingSync) {
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
        return userProfileRepository.findByUserId(userId)
                .map(profile -> (int) profile.getCompletedLectures().stream()
                        .filter(cl -> courseId.equals(cl.getCourseId()))
                        .map(UserProfile.CompletedLecture::getLectureLabel)
                        .distinct()
                        .count())
                .orElse(0);
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

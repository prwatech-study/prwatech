package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.CompleteLectureRequestDTO;
import com.prwatech.skillama.dto.DemoDashboardSeedResultDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.model.CourseCurriculum;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.model.UserCourseEnrollment;
import com.prwatech.skillama.model.UserCourseProgress;
import com.prwatech.skillama.model.UserProfile;
import com.prwatech.skillama.repository.CourseCurriculumRepository;
import com.prwatech.skillama.repository.CourseRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.repository.UserCourseEnrollmentRepository;
import com.prwatech.skillama.repository.UserCourseProgressRepository;
import com.prwatech.skillama.repository.UserLectureProgressRepository;
import com.prwatech.skillama.repository.UserProfileRepository;
import com.prwatech.skillama.util.IndiaTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Owner-only helper to inflate learner dashboard metrics for demos / screenshots.
 */
@Service
@RequiredArgsConstructor
public class DemoDashboardSeedService {

    /** Balanced mix: completed, in-progress, low, not started — repeats if more courses. */
    private static final int[] TARGET_PROGRESS_PATTERN = {100, 72, 48, 25, 0};

    private final SkillamaUserRepository userRepository;
    private final CourseRepository courseRepository;
    private final CourseCurriculumRepository curriculumRepository;
    private final UserCourseEnrollmentRepository enrollmentRepository;
    private final UserCourseProgressRepository courseProgressRepository;
    private final UserLectureProgressRepository lectureProgressRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserCourseService userCourseService;
    private final UserProfileService userProfileService;
    private final AdminService adminService;

    @Transactional
    public DemoDashboardSeedResultDTO seedForEmail(String email, String ownerId, boolean assignAllActiveCourses) {
        adminService.requireOwner(ownerId);

        if (!StringUtils.hasText(email)) {
            throw new IllegalArgumentException("email is required");
        }

        User user = userRepository.findByEmail(email.trim())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));

        if (user.getRole() == User.UserRole.ADMIN || user.getRole() == User.UserRole.OWNER) {
            throw new IllegalArgumentException(
                    "Demo seed applies to learner (USER) accounts. Use a learner login for dashboard screenshots.");
        }

        String userId = user.getId();

        if (assignAllActiveCourses) {
            List<String> activeCourseIds = courseRepository.findAll().stream()
                    .filter(CourseService::isActive)
                    .map(Course::getId)
                    .collect(Collectors.toList());
            if (!activeCourseIds.isEmpty()) {
                adminService.assignCourses(userId, activeCourseIds, ownerId);
            }
        }

        List<UserCourseEnrollment> enrollments = enrollmentRepository.findByUserIdAndStatus(
                userId, UserCourseEnrollment.EnrollmentStatus.ACTIVE);

        if (enrollments.isEmpty()) {
            throw new IllegalStateException(
                    "User has no active course enrollments. Assign courses first or pass assignAllActiveCourses=true.");
        }

        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseGet(() -> userProfileService.getOrCreateProfile(null, userId));

        List<DemoDashboardSeedResultDTO.CourseProgressSummary> summaries = new ArrayList<>();
        int courseIndex = 0;

        List<UserCourseEnrollment> ordered = enrollments.stream()
                .sorted(Comparator.comparing(e -> {
                    Course c = courseRepository.findById(e.getCourseId()).orElse(null);
                    return c != null ? c.getName() : e.getCourseId();
                }))
                .collect(Collectors.toList());

        for (UserCourseEnrollment enrollment : ordered) {
            String courseId = enrollment.getCourseId();
            Course course = courseRepository.findById(courseId).orElse(null);
            if (course == null || !CourseService.isActive(course)) {
                continue;
            }

            int targetPercent = TARGET_PROGRESS_PATTERN[courseIndex % TARGET_PROGRESS_PATTERN.length];
            courseIndex++;

            resetCourseProgress(userId, courseId, profile);
            userProfileRepository.save(profile);
            profile = userProfileRepository.findByUserId(userId).orElse(profile);

            List<LectureRef> lectures = collectEnabledLectures(courseId);
            int total = lectures.size();
            int toComplete = total > 0
                    ? Math.min(total, (int) Math.round(total * (targetPercent / 100.0)))
                    : 0;

            LocalDateTime accessedAt = IndiaTime.now().minusDays(Math.max(0, courseIndex - 1));

            for (int i = 0; i < toComplete; i++) {
                LectureRef lec = lectures.get(i);
                LocalDateTime completedAt = accessedAt.minusHours(Math.max(0, toComplete - i));

                userCourseService.updateProgress(userId, courseId, lec.label, true, 180 + i * 30);

                CompleteLectureRequestDTO req = CompleteLectureRequestDTO.builder()
                        .lectureLabel(lec.label)
                        .courseId(courseId)
                        .moduleName(lec.moduleName)
                        .timeSpent(180 + i * 30)
                        .completionPercentage(100)
                        .completedAt(completedAt)
                        .build();
                userProfileService.completeLecture(null, userId, req);
            }

            touchLastAccessed(userId, courseId, accessedAt, enrollment.getEnrolledAt());

            int progressPercent = total > 0 ? (int) Math.round((toComplete * 100.0) / total) : 0;
            summaries.add(DemoDashboardSeedResultDTO.CourseProgressSummary.builder()
                    .courseId(courseId)
                    .courseName(course.getName())
                    .progressPercent(progressPercent)
                    .completedLectures(toComplete)
                    .totalLectures(total)
                    .build());
        }

        userProfileRepository.save(profile);

        int avg = summaries.isEmpty() ? 0
                : (int) Math.round(summaries.stream()
                        .mapToInt(DemoDashboardSeedResultDTO.CourseProgressSummary::getProgressPercent)
                        .average()
                        .orElse(0));

        return DemoDashboardSeedResultDTO.builder()
                .userId(userId)
                .email(user.getEmail())
                .userName(user.getName())
                .coursesSeeded(summaries.size())
                .averageProgressPercent(avg)
                .courses(summaries)
                .build();
    }

    private void resetCourseProgress(String userId, String courseId, UserProfile profile) {
        lectureProgressRepository.findByUserIdAndCourseId(userId, courseId)
                .forEach(lectureProgressRepository::delete);

        profile.getCompletedLectures().removeIf(cl -> courseId.equals(cl.getCourseId()));
        profile.getInProgressLectures().removeIf(il -> courseId.equals(il.getCourseId()));

        courseProgressRepository.findByUserIdAndCourseId(userId, courseId)
                .ifPresent(p -> {
                    p.setProgress(0);
                    p.setCompletedLectures(0);
                    courseProgressRepository.save(p);
                });
    }

    private void touchLastAccessed(
            String userId, String courseId, LocalDateTime lastAccessed, LocalDateTime enrolledAt) {
        UserCourseProgress progress = courseProgressRepository
                .findByUserIdAndCourseId(userId, courseId)
                .orElseGet(() -> {
                    UserCourseProgress p = new UserCourseProgress();
                    p.setUserId(userId);
                    p.setCourseId(courseId);
                    p.setEnrolledAt(enrolledAt != null ? enrolledAt : IndiaTime.now());
                    p.setCreatedAt(IndiaTime.now());
                    return p;
                });
        progress.setLastAccessed(lastAccessed);
        progress.setUpdatedAt(IndiaTime.now());
        courseProgressRepository.save(progress);
    }

    private List<LectureRef> collectEnabledLectures(String courseId) {
        List<CourseCurriculum> modules = curriculumRepository.findByCourseIdOrderByOrderAsc(courseId);
        List<LectureRef> out = new ArrayList<>();
        for (CourseCurriculum module : modules) {
            if (module.getSubmodules() == null) continue;
            for (CourseCurriculum.Submodule sub : module.getSubmodules()) {
                if (!CourseService.isSubmoduleEnabled(sub)) continue;
                if (!StringUtils.hasText(sub.getLabel())) continue;
                out.add(new LectureRef(sub.getLabel(), module.getModuleName()));
            }
        }
        return out;
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

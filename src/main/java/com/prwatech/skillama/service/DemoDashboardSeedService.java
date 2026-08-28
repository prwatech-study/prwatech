package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.CompleteLectureRequestDTO;
import com.prwatech.skillama.dto.DemoDashboardSeedResultDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.model.CourseCurriculum;
import com.prwatech.skillama.model.ModuleQuizAttempt;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.model.UserCourseEnrollment;
import com.prwatech.skillama.model.UserCourseProgress;
import com.prwatech.skillama.model.UserProfile;
import com.prwatech.skillama.repository.CourseCurriculumRepository;
import com.prwatech.skillama.repository.CourseRepository;
import com.prwatech.skillama.repository.ModuleQuizAttemptRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Owner-only helper to inflate learner dashboard metrics for demos / screenshots.
 */
@Service
@RequiredArgsConstructor
public class DemoDashboardSeedService {

    /** Balanced mix: completed, in-progress, low, not started — repeats if more courses. */
    private static final int[] TARGET_PROGRESS_PATTERN = {100, 72, 48, 25, 0};

    /** Seeded quiz result: 4/5 = 80%, above the 70% pass mark but not suspiciously perfect. */
    private static final int SEED_QUIZ_SCORE = 4;
    private static final int SEED_QUIZ_MAX_SCORE = 5;

    private final SkillamaUserRepository userRepository;
    private final CourseRepository courseRepository;
    private final CourseCurriculumRepository curriculumRepository;
    private final UserCourseEnrollmentRepository enrollmentRepository;
    private final UserCourseProgressRepository courseProgressRepository;
    private final UserLectureProgressRepository lectureProgressRepository;
    private final UserProfileRepository userProfileRepository;
    private final ModuleQuizAttemptRepository moduleQuizAttemptRepository;
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

        // Ensure the profile exists; all later profile writes re-load a fresh copy.
        // Holding one snapshot across courses (as this used to) meant every save
        // overwrote the completions completeLecture had just written for the
        // previous course, leaving the LMS-side progress store nearly empty.
        userProfileRepository.findByUserId(userId)
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

            resetCourseProgress(userId, courseId);

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

            seedPassedModuleQuizzes(userId, courseId, lectures, toComplete, accessedAt);

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

    private void resetCourseProgress(String userId, String courseId) {
        lectureProgressRepository.findByUserIdAndCourseId(userId, courseId)
                .forEach(lectureProgressRepository::delete);
        moduleQuizAttemptRepository.deleteByUserIdAndCourseId(userId, courseId);

        userProfileRepository.findByUserId(userId).ifPresent(profile -> {
            profile.getCompletedLectures().removeIf(cl -> courseId.equals(cl.getCourseId()));
            profile.getInProgressLectures().removeIf(il -> courseId.equals(il.getCourseId()));
            if (profile.getPassedModuleQuizzes() != null) {
                profile.getPassedModuleQuizzes().removeIf(q -> courseId.equals(q.getCourseId()));
            }
            if (profile.getSkippedModuleQuizzes() != null) {
                profile.getSkippedModuleQuizzes().removeIf(q -> courseId.equals(q.getCourseId()));
            }
            userProfileRepository.save(profile);
        });

        courseProgressRepository.findByUserIdAndCourseId(userId, courseId)
                .ifPresent(p -> {
                    p.setProgress(0);
                    p.setCompletedLectures(0);
                    courseProgressRepository.save(p);
                });
    }

    /**
     * The LMS-side course % counts one quiz per module in its denominator and gates
     * the next module on a pass, so seeded lecture completions alone read low there
     * and leave "complete the quiz to unlock" walls mid-course. Mark the module quiz
     * passed (profile entry + a matching attempt row for the history panel) for every
     * module whose lectures were all seeded complete.
     */
    private void seedPassedModuleQuizzes(
            String userId, String courseId, List<LectureRef> lectures, int toComplete,
            LocalDateTime accessedAt) {
        Map<String, Integer> totalPerModule = new LinkedHashMap<>();
        Map<String, Integer> donePerModule = new LinkedHashMap<>();
        for (int i = 0; i < lectures.size(); i++) {
            String moduleName = lectures.get(i).moduleName;
            totalPerModule.merge(moduleName, 1, Integer::sum);
            if (i < toComplete) {
                donePerModule.merge(moduleName, 1, Integer::sum);
            }
        }
        List<String> fullyCompletedModules = totalPerModule.entrySet().stream()
                .filter(e -> Objects.equals(e.getValue(), donePerModule.get(e.getKey())))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        if (fullyCompletedModules.isEmpty()) {
            return;
        }

        UserProfile profile = userProfileRepository.findByUserId(userId).orElse(null);
        if (profile == null) {
            return;
        }
        if (profile.getPassedModuleQuizzes() == null) {
            profile.setPassedModuleQuizzes(new ArrayList<>());
        }

        int quizIndex = 0;
        for (String moduleName : fullyCompletedModules) {
            LocalDateTime passedAt =
                    accessedAt.minusMinutes(30L * (fullyCompletedModules.size() - quizIndex));
            profile.getPassedModuleQuizzes().add(UserProfile.PassedModuleQuiz.builder()
                    .courseId(courseId)
                    .moduleName(moduleName)
                    .passedAt(passedAt)
                    .bestScore(SEED_QUIZ_SCORE)
                    .maxScore(SEED_QUIZ_MAX_SCORE)
                    .build());
            moduleQuizAttemptRepository.save(ModuleQuizAttempt.builder()
                    .userId(userId)
                    .courseId(courseId)
                    .moduleName(moduleName)
                    .attemptNumber(1)
                    .score(SEED_QUIZ_SCORE)
                    .maxScore(SEED_QUIZ_MAX_SCORE)
                    .percentage(SEED_QUIZ_SCORE * 100.0 / SEED_QUIZ_MAX_SCORE)
                    .passed(true)
                    .timeSpentSeconds(150 + quizIndex * 20)
                    .overTimeLimit(false)
                    .submittedAt(passedAt)
                    .build());
            quizIndex++;
        }
        profile.setUpdatedAt(IndiaTime.now());
        userProfileRepository.save(profile);
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

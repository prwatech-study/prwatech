package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.DemoDashboardSeedResultDTO;
import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.model.CourseCurriculum;
import com.prwatech.skillama.model.ModuleQuizAttempt;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.model.UserCourseEnrollment;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The seeded LMS-side state must agree with the seeded dashboard numbers: module
 * quizzes are marked passed for fully-completed modules (the access-control
 * course % counts quizzes in its denominator and gates modules on a pass), and
 * stale per-course quiz state is pruned on reseed.
 */
@ExtendWith(MockitoExtension.class)
class DemoDashboardSeedServiceTest {

    private static final String USER_ID = "demo-1";
    private static final String OWNER_ID = "owner-1";
    private static final String COURSE_ID = "c1";
    private static final String DEMO_EMAIL = "demo@skillama.co.in";

    @Mock private SkillamaUserRepository userRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private CourseCurriculumRepository curriculumRepository;
    @Mock private UserCourseEnrollmentRepository enrollmentRepository;
    @Mock private UserCourseProgressRepository courseProgressRepository;
    @Mock private UserLectureProgressRepository lectureProgressRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private ModuleQuizAttemptRepository moduleQuizAttemptRepository;
    @Mock private UserCourseService userCourseService;
    @Mock private UserProfileService userProfileService;
    @Mock private AdminService adminService;

    @InjectMocks private DemoDashboardSeedService seedService;

    private UserProfile profile;

    @BeforeEach
    void setUp() {
        profile = new UserProfile();
    }

    private void stubHappyPath() {
        User user = User.builder()
                .id(USER_ID)
                .email(DEMO_EMAIL)
                .role(User.UserRole.USER)
                .active(true)
                .build();
        when(userRepository.findByEmail(DEMO_EMAIL)).thenReturn(Optional.of(user));

        when(enrollmentRepository.findByUserIdAndStatus(
                USER_ID, UserCourseEnrollment.EnrollmentStatus.ACTIVE))
                .thenReturn(List.of(UserCourseEnrollment.builder()
                        .userId(USER_ID)
                        .courseId(COURSE_ID)
                        .enrolledAt(IndiaTime.now().minusDays(30))
                        .status(UserCourseEnrollment.EnrollmentStatus.ACTIVE)
                        .build()));

        when(courseRepository.findById(COURSE_ID)).thenReturn(
                Optional.of(Course.builder().id(COURSE_ID).name("Python").build()));

        // 2 modules x 2 enabled lectures each. First course in the pattern -> 100%.
        when(curriculumRepository.findByCourseIdOrderByOrderAsc(COURSE_ID)).thenReturn(List.of(
                module("Module 1", "L1", "L2"),
                module("Module 2", "L3", "L4")));

        when(lectureProgressRepository.findByUserIdAndCourseId(USER_ID, COURSE_ID))
                .thenReturn(Collections.emptyList());
        when(courseProgressRepository.findByUserIdAndCourseId(USER_ID, COURSE_ID))
                .thenReturn(Optional.empty());

        when(userProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
    }

    private CourseCurriculum module(String name, String... labels) {
        List<CourseCurriculum.Submodule> subs = java.util.Arrays.stream(labels)
                .map(l -> {
                    CourseCurriculum.Submodule sub = new CourseCurriculum.Submodule();
                    sub.setLabel(l);
                    sub.setEnabled(true);
                    return sub;
                })
                .collect(Collectors.toList());
        return CourseCurriculum.builder()
                .courseId(COURSE_ID)
                .moduleName(name)
                .submodules(subs)
                .build();
    }

    @Test
    void seed_fullCompletion_marksEveryModuleQuizPassedWithAttemptRows() {
        stubHappyPath();

        DemoDashboardSeedResultDTO result = seedService.seedForEmail(DEMO_EMAIL, OWNER_ID, false);

        assertEquals(100, result.getAverageProgressPercent());
        verify(userProfileService, times(4)).completeLecture(eq(null), eq(USER_ID), any());
        verify(userCourseService, times(4))
                .updateProgress(eq(USER_ID), eq(COURSE_ID), anyString(), eq(true), anyInt());

        assertEquals(2, profile.getPassedModuleQuizzes().size());
        assertEquals(
                List.of("Module 1", "Module 2"),
                profile.getPassedModuleQuizzes().stream()
                        .map(UserProfile.PassedModuleQuiz::getModuleName)
                        .collect(Collectors.toList()));

        ArgumentCaptor<ModuleQuizAttempt> attempts = ArgumentCaptor.forClass(ModuleQuizAttempt.class);
        verify(moduleQuizAttemptRepository, times(2)).save(attempts.capture());
        assertTrue(attempts.getAllValues().stream().allMatch(a ->
                Boolean.TRUE.equals(a.getPassed())
                        && a.getPercentage() >= ModuleQuizService.PASSING_PERCENTAGE));

        verify(userProfileRepository, atLeastOnce()).save(profile);
    }

    @Test
    void seed_partialCompletion_marksOnlyFullyCompletedModules() {
        // 3 of 4 lectures -> module 1 complete, module 2 half done. Force the 72%
        // pattern slot by making this the second course alphabetically.
        stubHappyPath();
        when(enrollmentRepository.findByUserIdAndStatus(
                USER_ID, UserCourseEnrollment.EnrollmentStatus.ACTIVE))
                .thenReturn(List.of(
                        UserCourseEnrollment.builder().userId(USER_ID).courseId("c0")
                                .enrolledAt(IndiaTime.now()).status(UserCourseEnrollment.EnrollmentStatus.ACTIVE).build(),
                        UserCourseEnrollment.builder().userId(USER_ID).courseId(COURSE_ID)
                                .enrolledAt(IndiaTime.now()).status(UserCourseEnrollment.EnrollmentStatus.ACTIVE).build()));
        when(courseRepository.findById("c0")).thenReturn(
                Optional.of(Course.builder().id("c0").name("Aws").build()));
        when(curriculumRepository.findByCourseIdOrderByOrderAsc("c0"))
                .thenReturn(List.of(module("Intro", "A1")));
        when(lectureProgressRepository.findByUserIdAndCourseId(USER_ID, "c0"))
                .thenReturn(Collections.emptyList());
        when(courseProgressRepository.findByUserIdAndCourseId(USER_ID, "c0"))
                .thenReturn(Optional.empty());

        seedService.seedForEmail(DEMO_EMAIL, OWNER_ID, false);

        // "Python" (c1) got the 72% slot: round(4 * 0.72) = 3 lectures -> only Module 1 fully done.
        List<String> c1Quizzes = profile.getPassedModuleQuizzes().stream()
                .filter(q -> COURSE_ID.equals(q.getCourseId()))
                .map(UserProfile.PassedModuleQuiz::getModuleName)
                .collect(Collectors.toList());
        assertEquals(List.of("Module 1"), c1Quizzes);
    }

    @Test
    void seed_prunesStaleQuizStateForTheCourseButKeepsOtherCourses() {
        stubHappyPath();
        profile.getPassedModuleQuizzes().add(UserProfile.PassedModuleQuiz.builder()
                .courseId(COURSE_ID).moduleName("Old Module").build());
        profile.getSkippedModuleQuizzes().add(UserProfile.SkippedModuleQuiz.builder()
                .courseId(COURSE_ID).moduleName("Old Module").build());
        profile.getPassedModuleQuizzes().add(UserProfile.PassedModuleQuiz.builder()
                .courseId("other-course").moduleName("Keep Me").build());

        seedService.seedForEmail(DEMO_EMAIL, OWNER_ID, false);

        verify(moduleQuizAttemptRepository).deleteByUserIdAndCourseId(USER_ID, COURSE_ID);
        List<String> passedModules = profile.getPassedModuleQuizzes().stream()
                .map(q -> q.getCourseId() + ":" + q.getModuleName())
                .collect(Collectors.toList());
        assertTrue(passedModules.contains("other-course:Keep Me"));
        assertTrue(passedModules.stream().noneMatch(m -> m.equals(COURSE_ID + ":Old Module")));
        assertTrue(profile.getSkippedModuleQuizzes().isEmpty());
    }

    @Test
    void seed_adminTarget_rejectedBeforeAnyWrites() {
        when(userRepository.findByEmail(DEMO_EMAIL)).thenReturn(Optional.of(
                User.builder().id(USER_ID).email(DEMO_EMAIL).role(User.UserRole.ADMIN).build()));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> seedService.seedForEmail(DEMO_EMAIL, OWNER_ID, false));
        verify(moduleQuizAttemptRepository, org.mockito.Mockito.never())
                .deleteByUserIdAndCourseId(anyString(), anyString());
        verify(userCourseService, org.mockito.Mockito.never())
                .updateProgress(anyString(), anyString(), anyString(), anyBoolean(), anyInt());
    }
}

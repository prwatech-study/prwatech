package com.prwatech.skillama.service;

import com.prwatech.common.exception.ForbiddenException;
import com.prwatech.skillama.dto.ReconcileProgressRequestDTO;
import com.prwatech.skillama.dto.ReconcileProgressResultDTO;
import com.prwatech.skillama.model.CourseCurriculum;
import com.prwatech.skillama.model.UserCourseProgress;
import com.prwatech.skillama.model.UserLectureProgress;
import com.prwatech.skillama.model.UserProfile;
import com.prwatech.skillama.repository.CourseCurriculumRepository;
import com.prwatech.skillama.repository.UserCourseEnrollmentRepository;
import com.prwatech.skillama.repository.UserCourseProgressRepository;
import com.prwatech.skillama.repository.UserLectureProgressRepository;
import com.prwatech.skillama.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProgressReconciliationServiceTest {

    @Mock private UserProfileService userProfileService;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private UserLectureProgressRepository lectureProgressRepository;
    @Mock private UserCourseProgressRepository courseProgressRepository;
    @Mock private CourseCurriculumRepository curriculumRepository;
    @Mock private UserCourseAccessService userCourseAccessService;
    @Mock private UserCourseEnrollmentRepository enrollmentRepository;

    @InjectMocks
    private ProgressReconciliationService progressReconciliationService;

    private static final String USER_ID = "u1";
    private static final String COURSE_ID = "course-1";

    @BeforeEach
    void stubCurriculum() {
        CourseCurriculum module = new CourseCurriculum();
        module.setModuleName("Module 1");
        CourseCurriculum.Submodule sub1 = new CourseCurriculum.Submodule();
        sub1.setLabel("Lecture 1");
        sub1.setEnabled(true);
        CourseCurriculum.Submodule sub2 = new CourseCurriculum.Submodule();
        sub2.setLabel("Lecture 2");
        sub2.setEnabled(true);
        module.setSubmodules(List.of(sub1, sub2));
        when(curriculumRepository.findByCourseIdOrderByOrderAsc(COURSE_ID)).thenReturn(List.of(module));
    }

    @Test
    void reconcileForUser_throwsWhenNotEnrolled() {
        org.mockito.Mockito.doThrow(new ForbiddenException("not enrolled"))
                .when(userCourseAccessService).assertCanAccessCourse(USER_ID, COURSE_ID);

        ReconcileProgressRequestDTO request = ReconcileProgressRequestDTO.builder()
                .courseId(COURSE_ID)
                .build();

        assertThrows(ForbiddenException.class,
                () -> progressReconciliationService.reconcileForUser(USER_ID, request));
    }

    @Test
    void reconcileForUser_skipsWritesWhenAlreadySynced() {
        when(userProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(
                UserProfile.builder()
                        .userId(USER_ID)
                        .completedLectures(new ArrayList<>(List.of(
                                UserProfile.CompletedLecture.builder()
                                        .courseId(COURSE_ID)
                                        .lectureLabel("Lecture 1")
                                        .build(),
                                UserProfile.CompletedLecture.builder()
                                        .courseId(COURSE_ID)
                                        .lectureLabel("Lecture 2")
                                        .build())))
                        .build()));
        when(lectureProgressRepository.findByUserIdAndCourseId(USER_ID, COURSE_ID))
                .thenReturn(List.of());
        when(courseProgressRepository.findByUserIdAndCourseId(USER_ID, COURSE_ID))
                .thenReturn(Optional.empty());

        ReconcileProgressResultDTO result = progressReconciliationService.reconcileForUserAsAdmin(
                USER_ID,
                ReconcileProgressRequestDTO.builder().courseId(COURSE_ID).build());

        assertEquals(0, result.getSyncedLectures());
        assertEquals(2, result.getTotalCompletedLectures());
        verify(userProfileService, never()).completeLecture(any(), any(), any());
    }

    @Test
    void reconcileForUser_syncsLectureProgressMissingFromProfile() {
        when(userProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(
                UserProfile.builder()
                        .userId(USER_ID)
                        .completedLectures(new ArrayList<>())
                        .build()));
        UserLectureProgress lp = new UserLectureProgress();
        lp.setLectureId("Lecture 1");
        lp.setCompleted(true);
        when(lectureProgressRepository.findByUserIdAndCourseId(USER_ID, COURSE_ID))
                .thenReturn(List.of(lp));
        when(courseProgressRepository.findByUserIdAndCourseId(USER_ID, COURSE_ID))
                .thenReturn(Optional.empty());

        ReconcileProgressResultDTO result = progressReconciliationService.reconcileForUserAsAdmin(
                USER_ID,
                ReconcileProgressRequestDTO.builder().courseId(COURSE_ID).build());

        assertEquals(1, result.getSyncedLectures());
        verify(userProfileService).completeLecture(isNull(), eq(USER_ID), any());
    }
}

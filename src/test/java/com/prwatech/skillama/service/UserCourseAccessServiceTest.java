package com.prwatech.skillama.service;

import com.prwatech.common.exception.ForbiddenException;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.model.UserCourseEnrollment;
import com.prwatech.skillama.repository.CourseRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.repository.UserCourseEnrollmentRepository;
import com.prwatech.skillama.repository.UserCourseProgressRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserCourseAccessServiceTest {

    @Mock
    private CourseRepository courseRepository;
    @Mock
    private UserCourseEnrollmentRepository enrollmentRepository;
    @Mock
    private UserCourseProgressRepository progressRepository;
    @Mock
    private SkillamaUserRepository userRepository;

    @InjectMocks
    private UserCourseAccessService userCourseAccessService;

    @Test
    void assertCanAccessCourse_throwsWhenNotEnrolled() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(User.builder().id("u1").role(User.UserRole.USER).build()));
        when(enrollmentRepository.findByUserIdAndCourseId("u1", "c1")).thenReturn(Optional.empty());

        assertThrows(ForbiddenException.class, () -> userCourseAccessService.assertCanAccessCourse("u1", "c1"));
    }

    @Test
    void assertCanAccessCourse_allowsActiveEnrollment() {
        UserCourseEnrollment enrollment = new UserCourseEnrollment();
        enrollment.setStatus(UserCourseEnrollment.EnrollmentStatus.ACTIVE);
        when(userRepository.findById("u1")).thenReturn(Optional.of(User.builder().id("u1").role(User.UserRole.USER).build()));
        when(enrollmentRepository.findByUserIdAndCourseId("u1", "c1")).thenReturn(Optional.of(enrollment));

        assertDoesNotThrow(() -> userCourseAccessService.assertCanAccessCourse("u1", "c1"));
    }
}

package com.prwatech.skillama.controller;

import com.prwatech.common.Constants;
import com.prwatech.skillama.exception.SkillamaAuthException;
import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.CourseRepository;
import com.prwatech.skillama.repository.UserCourseEnrollmentRepository;
import com.prwatech.skillama.service.SkillamaAuthSupport;
import com.prwatech.skillama.service.UserCourseService;
import com.prwatech.skillama.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * These endpoints previously had NO auth gating at all (self-documented gap in the
 * original code). Locking that down is the whole point — every test here exists to
 * prove a caller without the right role gets rejected, and ADMIN/OWNER/TESTER pass.
 */
@ExtendWith(MockitoExtension.class)
class AdminCourseEnrollmentControllerTest {

    private MockMvc mockMvc;

    @Mock private UserCourseService userCourseService;
    @Mock private UserService userService;
    @Mock private CourseRepository courseRepository;
    @Mock private UserCourseEnrollmentRepository enrollmentRepository;
    @Mock private SkillamaAuthSupport skillamaAuthSupport;

    private static final String TOKEN = "Bearer valid.jwt.token";

    @BeforeEach
    void setUp() {
        AdminCourseEnrollmentController controller = new AdminCourseEnrollmentController(
                userCourseService, userService, courseRepository, enrollmentRepository, skillamaAuthSupport);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    private User withRole(User.UserRole role) {
        return User.builder().id("u1").email("u1@x.com").role(role).active(true).build();
    }

    @Test
    void enrollmentStats_withoutAuth_returns401() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any()))
                .thenThrow(new SkillamaAuthException("Session expired. Please sign in again."));

        mockMvc.perform(get("/skillama/api/admin/courses/enrollments/stats"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void enrollmentStats_asLearner_returns403() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any())).thenReturn("u1");
        when(userService.findById("u1")).thenReturn(Optional.of(withRole(User.UserRole.USER)));

        mockMvc.perform(get("/skillama/api/admin/courses/enrollments/stats").header(Constants.AUTH, TOKEN))
                .andExpect(status().isForbidden());
    }

    @Test
    void enrollmentStats_asAdmin_returns200() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any())).thenReturn("u1");
        when(userService.findById("u1")).thenReturn(Optional.of(withRole(User.UserRole.ADMIN)));
        when(enrollmentRepository.count()).thenReturn(0L);
        when(enrollmentRepository.findAll()).thenReturn(java.util.List.of());

        mockMvc.perform(get("/skillama/api/admin/courses/enrollments/stats").header(Constants.AUTH, TOKEN))
                .andExpect(status().isOk());
    }

    @Test
    void enrollmentStats_asTester_returns200() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any())).thenReturn("u1");
        when(userService.findById("u1")).thenReturn(Optional.of(withRole(User.UserRole.TESTER)));
        when(enrollmentRepository.count()).thenReturn(0L);
        when(enrollmentRepository.findAll()).thenReturn(java.util.List.of());

        mockMvc.perform(get("/skillama/api/admin/courses/enrollments/stats").header(Constants.AUTH, TOKEN))
                .andExpect(status().isOk());
    }

    @Test
    void enrollAllUsersToAllCourses_asLearner_returns403() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any())).thenReturn("u1");
        when(userService.findById("u1")).thenReturn(Optional.of(withRole(User.UserRole.USER)));

        mockMvc.perform(post("/skillama/api/admin/courses/enroll-all-to-all").header(Constants.AUTH, TOKEN))
                .andExpect(status().isForbidden());
    }

    @Test
    void enrollUserToCourse_asAdmin_returns200() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any())).thenReturn("admin1");
        when(userService.findById("admin1")).thenReturn(Optional.of(withRole(User.UserRole.ADMIN)));

        Course course = new Course();
        course.setId("course-1");
        when(userCourseService.enrollUserToCourse(any(), any(), any())).thenReturn(
                new com.prwatech.skillama.model.UserCourseEnrollment());

        mockMvc.perform(post("/skillama/api/admin/courses/course-1/enroll/learner-1").header(Constants.AUTH, TOKEN))
                .andExpect(status().isOk());
    }
}

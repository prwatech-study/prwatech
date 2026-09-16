package com.prwatech.skillama.controller;

import com.prwatech.authentication.security.JwtUtils;
import com.prwatech.common.Constants;
import com.prwatech.skillama.model.CourseCurriculum;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.service.CourseService;
import com.prwatech.skillama.service.CourseStudyMaterialService;
import com.prwatech.skillama.service.GlobalAiExamCourseService;
import com.prwatech.skillama.service.UserCourseAccessService;
import com.prwatech.skillama.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CourseControllerGlobalAiExamTest {

    private MockMvc mockMvc;

    @Mock private CourseService courseService;
    @Mock private CourseStudyMaterialService studyMaterialService;
    @Mock private JwtUtils jwtUtils;
    @Mock private UserService userService;
    @Mock private UserCourseAccessService userCourseAccessService;
    @Mock private GlobalAiExamCourseService globalAiExamCourseService;
    @Mock private com.prwatech.skillama.service.CourseDetailContentService courseDetailContentService;
    @Mock private com.prwatech.skillama.service.AdminPermissionService adminPermissionService;
    @Mock private com.prwatech.skillama.service.SkillamaAuthSupport skillamaAuthSupport;

    private static final String TOKEN = "Bearer valid.jwt.token";

    @BeforeEach
    void setUp() {
        CourseController controller = new CourseController(
                courseService, studyMaterialService, jwtUtils, userService,
                userCourseAccessService, globalAiExamCourseService, courseDetailContentService,
                adminPermissionService, skillamaAuthSupport);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
        when(jwtUtils.extractUsername("valid.jwt.token")).thenReturn("learner@example.com");
        when(userService.findByEmail("learner@example.com"))
                .thenReturn(Optional.of(User.builder().id("u1").email("learner@example.com").role(User.UserRole.USER).build()));
    }

    @Test
    void curriculumAllowedForGloballyEnabledCourseWithoutEnrollment() throws Exception {
        when(globalAiExamCourseService.isEnabled("c1")).thenReturn(true);
        when(courseService.getCurriculumByCourseIdOrdered(eq("c1"), eq(false), eq(false)))
                .thenReturn(List.of(new CourseCurriculum()));

        mockMvc.perform(get("/skillama/courses/c1/curriculum")
                        .header(Constants.AUTH, TOKEN))
                .andExpect(status().isOk());

        verify(userCourseAccessService, never()).hasActiveEnrollment("u1", "c1");
        verify(userCourseAccessService, never()).touchLastAccessed("u1", "c1");
    }

    @Test
    void curriculumDeniedForNonGlobalCourseWithoutEnrollment() throws Exception {
        when(globalAiExamCourseService.isEnabled("c2")).thenReturn(false);
        when(userCourseAccessService.isAdminOrOwner("u1")).thenReturn(false);
        when(userCourseAccessService.hasActiveEnrollment("u1", "c2")).thenReturn(false);

        mockMvc.perform(get("/skillama/courses/c2/curriculum")
                        .header(Constants.AUTH, TOKEN))
                .andExpect(status().isForbidden());

        verify(courseService, never()).getCurriculumByCourseIdOrdered(eq("c2"), anyBoolean(), anyBoolean());
    }
}

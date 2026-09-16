package com.prwatech.skillama.controller;

import com.prwatech.authentication.security.JwtUtils;
import com.prwatech.common.Constants;
import com.prwatech.skillama.dto.StudyMaterialDTO;
import com.prwatech.skillama.exception.SkillamaAuthException;
import com.prwatech.skillama.model.AdminModule;
import com.prwatech.skillama.model.AdminPermissionAction;
import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.service.AdminPermissionService;
import com.prwatech.skillama.service.CourseDetailContentService;
import com.prwatech.skillama.service.CourseService;
import com.prwatech.skillama.service.CourseStudyMaterialService;
import com.prwatech.skillama.service.GlobalAiExamCourseService;
import com.prwatech.skillama.service.SkillamaAuthSupport;
import com.prwatech.skillama.service.UserCourseAccessService;
import com.prwatech.skillama.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CourseControllerAccessTest {

    private MockMvc mockMvc;

    @Mock private CourseService courseService;
    @Mock private CourseStudyMaterialService studyMaterialService;
    @Mock private JwtUtils jwtUtils;
    @Mock private UserService userService;
    @Mock private UserCourseAccessService userCourseAccessService;
    @Mock private GlobalAiExamCourseService globalAiExamCourseService;
    @Mock private CourseDetailContentService courseDetailContentService;
    @Mock private AdminPermissionService adminPermissionService;
    @Mock private SkillamaAuthSupport skillamaAuthSupport;

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
    }

    @Test
    void createCourse_withoutAuth_returns401() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any()))
                .thenThrow(new SkillamaAuthException("Session expired. Please sign in again."));

        mockMvc.perform(post("/skillama/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        verify(courseService, never()).create(any());
    }

    @Test
    void createCourse_asLearner_returns403() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any())).thenReturn("u1");
        doThrow(new RuntimeException("Insufficient permission for COURSES (CREATE)"))
                .when(adminPermissionService)
                .requirePermission("u1", AdminModule.COURSES, AdminPermissionAction.CREATE);

        mockMvc.perform(post("/skillama/courses")
                        .header(Constants.AUTH, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        verify(courseService, never()).create(any());
    }

    @Test
    void createCourse_withCoursePermission_returns200() throws Exception {
        Course created = Course.builder().id("c1").name("New").build();
        when(skillamaAuthSupport.resolveUserIdFromRequest(any())).thenReturn("admin1");
        when(courseService.create(any())).thenReturn(created);

        mockMvc.perform(post("/skillama/courses")
                        .header(Constants.AUTH, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"New\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void updateCourse_withoutAuth_returns401() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any()))
                .thenThrow(new SkillamaAuthException("Session expired. Please sign in again."));

        mockMvc.perform(put("/skillama/courses/c1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMaterials_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/skillama/courses/c1/materials"))
                .andExpect(status().isUnauthorized());
        verify(studyMaterialService, never()).listForCourse(any());
    }

    @Test
    void getMaterials_withoutEnrollment_returns403() throws Exception {
        when(jwtUtils.extractUsername("valid.jwt.token")).thenReturn("learner@example.com");
        when(userService.findByEmail("learner@example.com"))
                .thenReturn(Optional.of(User.builder().id("u1").email("learner@example.com").role(User.UserRole.USER).build()));
        when(userCourseAccessService.isAdminOrOwner("u1")).thenReturn(false);
        when(userCourseAccessService.hasActiveEnrollment("u1", "c1")).thenReturn(false);

        mockMvc.perform(get("/skillama/courses/c1/materials").header(Constants.AUTH, TOKEN))
                .andExpect(status().isForbidden());
        verify(studyMaterialService, never()).listForCourse("c1");
    }

    @Test
    void getMaterials_withEnrollment_returns200() throws Exception {
        when(jwtUtils.extractUsername("valid.jwt.token")).thenReturn("learner@example.com");
        when(userService.findByEmail("learner@example.com"))
                .thenReturn(Optional.of(User.builder().id("u1").email("learner@example.com").role(User.UserRole.USER).build()));
        when(userCourseAccessService.isAdminOrOwner("u1")).thenReturn(false);
        when(userCourseAccessService.hasActiveEnrollment("u1", "c1")).thenReturn(true);
        when(courseService.findById("c1")).thenReturn(Optional.of(Course.builder().id("c1").active(true).build()));
        when(studyMaterialService.listForCourse("c1"))
                .thenReturn(List.of(StudyMaterialDTO.builder().id("m1").courseId("c1").build()));

        mockMvc.perform(get("/skillama/courses/c1/materials").header(Constants.AUTH, TOKEN))
                .andExpect(status().isOk());
        verify(studyMaterialService).listForCourse(eq("c1"));
    }
}

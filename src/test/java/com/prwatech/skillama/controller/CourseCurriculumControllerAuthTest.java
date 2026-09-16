package com.prwatech.skillama.controller;

import com.prwatech.authentication.security.JwtUtils;
import com.prwatech.common.Constants;
import com.prwatech.skillama.exception.SkillamaAuthException;
import com.prwatech.skillama.model.AdminModule;
import com.prwatech.skillama.model.AdminPermissionAction;
import com.prwatech.skillama.model.CourseCurriculum;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.CourseCurriculumRepository;
import com.prwatech.skillama.service.AdminAuditService;
import com.prwatech.skillama.service.AdminPermissionService;
import com.prwatech.skillama.service.CourseCurriculumService;
import com.prwatech.skillama.service.CourseService;
import com.prwatech.skillama.service.SkillamaAuthSupport;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CourseCurriculumControllerAuthTest {

    private MockMvc mockMvc;

    @Mock private CourseCurriculumService curriculumService;
    @Mock private CourseService courseService;
    @Mock private CourseCurriculumRepository curriculumRepo;
    @Mock private AdminAuditService adminAuditService;
    @Mock private AdminPermissionService adminPermissionService;
    @Mock private JwtUtils jwtUtils;
    @Mock private UserService userService;
    @Mock private SkillamaAuthSupport skillamaAuthSupport;

    private static final String TOKEN = "Bearer valid.jwt.token";

    @BeforeEach
    void setUp() {
        CourseCurriculumController controller = new CourseCurriculumController(
                curriculumService, courseService, curriculumRepo, adminAuditService,
                adminPermissionService, jwtUtils, userService, skillamaAuthSupport);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    void getById_withoutAuth_returns401() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any()))
                .thenThrow(new SkillamaAuthException("Session expired. Please sign in again."));

        mockMvc.perform(get("/skillama/curriculum/mod-1"))
                .andExpect(status().isUnauthorized());
        verify(curriculumService, never()).findById(any());
    }

    @Test
    void getById_asLearner_returns403() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any())).thenReturn("u1");
        when(userService.findById("u1")).thenReturn(Optional.of(
                User.builder().id("u1").role(User.UserRole.USER).build()));

        mockMvc.perform(get("/skillama/curriculum/mod-1").header(Constants.AUTH, TOKEN))
                .andExpect(status().isForbidden());
        verify(curriculumService, never()).findById(any());
    }

    @Test
    void getById_asAdminWithoutCurriculumRead_returns403() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any())).thenReturn("admin1");
        when(userService.findById("admin1")).thenReturn(Optional.of(
                User.builder().id("admin1").role(User.UserRole.ADMIN).build()));
        doThrow(new RuntimeException("Insufficient permission for CURRICULUM (READ)"))
                .when(adminPermissionService)
                .requirePermission("admin1", AdminModule.CURRICULUM, AdminPermissionAction.READ);

        mockMvc.perform(get("/skillama/curriculum/mod-1").header(Constants.AUTH, TOKEN))
                .andExpect(status().isForbidden());
        verify(curriculumService, never()).findById(any());
    }

    @Test
    void getById_asAdminWithPermission_returns200() throws Exception {
        CourseCurriculum module = new CourseCurriculum();
        module.setId("mod-1");
        when(skillamaAuthSupport.resolveUserIdFromRequest(any())).thenReturn("admin1");
        when(userService.findById("admin1")).thenReturn(Optional.of(
                User.builder().id("admin1").role(User.UserRole.ADMIN).build()));
        when(curriculumService.findById("mod-1")).thenReturn(Optional.of(module));

        mockMvc.perform(get("/skillama/curriculum/mod-1").header(Constants.AUTH, TOKEN))
                .andExpect(status().isOk());
    }
}

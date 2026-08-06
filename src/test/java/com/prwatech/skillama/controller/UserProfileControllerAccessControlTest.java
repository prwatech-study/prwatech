package com.prwatech.skillama.controller;

import com.prwatech.authentication.security.JwtUtils;
import com.prwatech.common.Constants;
import com.prwatech.skillama.dto.AccessControlResponseDTO;
import com.prwatech.skillama.dto.ReconcileProgressRequestDTO;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.service.CourseShareService;
import com.prwatech.skillama.service.FreemiumService;
import com.prwatech.skillama.service.AiUsageService;
import com.prwatech.skillama.service.LmsThemeService;
import com.prwatech.skillama.service.ModuleQuizService;
import com.prwatech.skillama.service.ProgressReconciliationService;
import com.prwatech.skillama.service.ReferralShareService;
import com.prwatech.skillama.service.UpgradeRequestService;
import com.prwatech.skillama.service.UserProfileService;
import com.prwatech.skillama.service.UserService;
import com.prwatech.skillama.service.SkillamaAuthSupport;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserProfileControllerAccessControlTest {

    private MockMvc mockMvc;

    @Mock private UserProfileService userProfileService;
    @Mock private UserService userService;
    @Mock private FreemiumService freemiumService;
    @Mock private ReferralShareService referralShareService;
    @Mock private CourseShareService courseShareService;
    @Mock private UpgradeRequestService upgradeRequestService;
    @Mock private LmsThemeService lmsThemeService;
    @Mock private ProgressReconciliationService progressReconciliationService;
    @Mock private ModuleQuizService moduleQuizService;
    @Mock private JwtUtils jwtUtils;
    @Mock private SkillamaAuthSupport skillamaAuthSupport;
    @Mock private AiUsageService aiUsageService;

    private static final String TOKEN = "Bearer valid.jwt.token";
    private static final User USER = User.builder()
            .id("u1")
            .email("learner@skillama.co.in")
            .active(true)
            .build();

    @BeforeEach
    void setUp() {
        UserProfileController controller = new UserProfileController(
                userProfileService,
                userService,
                freemiumService,
                referralShareService,
                courseShareService,
                upgradeRequestService,
                lmsThemeService,
                progressReconciliationService,
                moduleQuizService,
                jwtUtils,
                skillamaAuthSupport,
                aiUsageService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    void getAccessControl_withoutAuthAndCourseId_returns401() throws Exception {
        mockMvc.perform(get("/skillama/user-profile/access-control")
                        .param("courseId", "course-1"))
                .andExpect(status().isUnauthorized());

        verify(progressReconciliationService, never()).reconcileForUser(any(), any());
    }

    @Test
    void getAccessControl_defaultSkipsReconcile() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any())).thenReturn("u1");
        when(userProfileService.getAccessControl(isNull(), eq("u1"), eq("course-1")))
                .thenReturn(AccessControlResponseDTO.builder().courseId("course-1").build());

        mockMvc.perform(get("/skillama/user-profile/access-control")
                        .param("courseId", "course-1")
                        .header(Constants.AUTH, TOKEN))
                .andExpect(status().isOk());

        verify(progressReconciliationService, never()).reconcileForUser(any(), any());
    }

    @Test
    void getAccessControl_withReconcileTrue_invokesReconcileForUser() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any())).thenReturn("u1");
        when(userProfileService.getAccessControl(isNull(), eq("u1"), eq("course-1")))
                .thenReturn(AccessControlResponseDTO.builder().courseId("course-1").build());

        mockMvc.perform(get("/skillama/user-profile/access-control")
                        .param("courseId", "course-1")
                        .param("reconcile", "true")
                        .header(Constants.AUTH, TOKEN))
                .andExpect(status().isOk());

        verify(progressReconciliationService).reconcileForUser(
                eq("u1"),
                eq(ReconcileProgressRequestDTO.builder().courseId("course-1").build()));
    }
}

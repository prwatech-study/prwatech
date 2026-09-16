package com.prwatech.skillama.controller;

import com.prwatech.authentication.security.JwtUtils;
import com.prwatech.common.Constants;
import com.prwatech.skillama.dto.CourseAnalyticsDTO;
import com.prwatech.skillama.exception.SkillamaAuthException;
import com.prwatech.skillama.model.AdminModule;
import com.prwatech.skillama.model.AdminPermissionAction;
import com.prwatech.skillama.script.GuestCourseMigrationScript;
import com.prwatech.skillama.service.AdminAuditService;
import com.prwatech.skillama.service.AdminPermissionService;
import com.prwatech.skillama.service.AdminService;
import com.prwatech.skillama.service.AiUsageService;
import com.prwatech.skillama.service.CodeAssistService;
import com.prwatech.skillama.service.CourseService;
import com.prwatech.skillama.service.DemoDashboardSeedService;
import com.prwatech.skillama.service.DemoResetService;
import com.prwatech.skillama.service.DoubtService;
import com.prwatech.skillama.service.ExamService;
import com.prwatech.skillama.service.FreemiumService;
import com.prwatech.skillama.service.IssueReportService;
import com.prwatech.skillama.service.LmsThemeService;
import com.prwatech.skillama.service.ModuleQuizService;
import com.prwatech.skillama.service.NotificationSettingsService;
import com.prwatech.skillama.service.PlatformAiSettingsService;
import com.prwatech.skillama.service.PlatformDemoVideoService;
import com.prwatech.skillama.service.PlatformThemeSettingsService;
import com.prwatech.skillama.service.ProgressReconciliationService;
import com.prwatech.skillama.service.ReferralShareService;
import com.prwatech.skillama.service.ReviewService;
import com.prwatech.skillama.service.SalesLeadService;
import com.prwatech.skillama.service.SkillamaAuthSupport;
import com.prwatech.skillama.service.SkillamaPlatformConfigService;
import com.prwatech.skillama.service.UpgradeRequestService;
import com.prwatech.skillama.service.UserProfileService;
import com.prwatech.skillama.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminCourseAnalyticsControllerTest {

    private MockMvc mockMvc;

    @Mock private AdminService adminService;
    @Mock private AdminPermissionService adminPermissionService;
    @Mock private SkillamaAuthSupport skillamaAuthSupport;

    private static final String TOKEN = "Bearer valid.jwt.token";

    @BeforeEach
    void setUp() {
        AdminController controller = new AdminController(
                adminService,
                mock(UserService.class),
                mock(CourseService.class),
                mock(JwtUtils.class),
                mock(GuestCourseMigrationScript.class),
                mock(FreemiumService.class),
                mock(SalesLeadService.class),
                mock(ReviewService.class),
                mock(IssueReportService.class),
                mock(PlatformDemoVideoService.class),
                mock(PlatformAiSettingsService.class),
                mock(PlatformThemeSettingsService.class),
                mock(ReferralShareService.class),
                mock(NotificationSettingsService.class),
                mock(AdminAuditService.class),
                mock(UpgradeRequestService.class),
                mock(LmsThemeService.class),
                mock(SkillamaPlatformConfigService.class),
                adminPermissionService,
                mock(DemoDashboardSeedService.class),
                mock(DemoResetService.class),
                mock(UserProfileService.class),
                mock(ProgressReconciliationService.class),
                skillamaAuthSupport,
                mock(DoubtService.class),
                mock(ExamService.class),
                mock(ModuleQuizService.class),
                mock(CodeAssistService.class),
                mock(AiUsageService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    void getCourseAnalytics_withoutAuth_returns401() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any()))
                .thenThrow(new SkillamaAuthException("Session expired. Please sign in again."));

        mockMvc.perform(get("/skillama/api/admin/analytics/courses/c1"))
                .andExpect(status().isUnauthorized());
        verify(adminService, never()).getCourseAnalytics(any());
    }

    @Test
    void getCourseAnalytics_asLearner_returns403() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any())).thenReturn("u1");
        doThrow(new RuntimeException("Admin access required"))
                .when(adminPermissionService)
                .requirePermission("u1", AdminModule.ANALYTICS, AdminPermissionAction.READ);

        mockMvc.perform(get("/skillama/api/admin/analytics/courses/c1").header(Constants.AUTH, TOKEN))
                .andExpect(status().isForbidden());
        verify(adminService, never()).getCourseAnalytics(any());
    }

    @Test
    void getCourseAnalytics_withAnalyticsRead_returns200() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any())).thenReturn("admin1");
        when(adminService.getCourseAnalytics("c1"))
                .thenReturn(new CourseAnalyticsDTO("c1", "Python", 1L, 1L, 0L, 10.0, 0.0));

        mockMvc.perform(get("/skillama/api/admin/analytics/courses/c1").header(Constants.AUTH, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }
}

package com.prwatech.skillama.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prwatech.authentication.security.JwtUtils;
import com.prwatech.common.Constants;
import com.prwatech.skillama.dto.CreateModuleQuizSessionResponseDTO;
import com.prwatech.skillama.dto.ModuleQuizAttemptResultDTO;
import com.prwatech.skillama.service.AiUsageService;
import com.prwatech.skillama.service.CourseShareService;
import com.prwatech.skillama.service.FreemiumService;
import com.prwatech.skillama.service.LmsThemeService;
import com.prwatech.skillama.service.ModuleQuizService;
import com.prwatech.skillama.service.ProgressReconciliationService;
import com.prwatech.skillama.service.ReferralShareService;
import com.prwatech.skillama.service.SkillamaAuthSupport;
import com.prwatech.skillama.service.UpgradeRequestService;
import com.prwatech.skillama.service.UserProfileService;
import com.prwatech.skillama.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserProfileControllerModuleQuizTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

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

    @BeforeEach
    void setUp() {
        UserProfileController controller = new UserProfileController(
                userProfileService, userService, freemiumService, referralShareService,
                courseShareService, upgradeRequestService, lmsThemeService, progressReconciliationService,
                moduleQuizService, jwtUtils, skillamaAuthSupport, aiUsageService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    void createSessionWithoutAuthReturns401() throws Exception {
        mockMvc.perform(post("/skillama/user-profile/module-quiz/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createSessionAuthenticatedDelegatesToServiceWithUserId() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any())).thenReturn("u1");
        when(moduleQuizService.createSession(isNull(), eq("u1"), any()))
                .thenReturn(CreateModuleQuizSessionResponseDTO.builder()
                        .sessionId("quiz-1").totalQuestions(2).build());

        mockMvc.perform(post("/skillama/user-profile/module-quiz/sessions")
                        .header(Constants.AUTH, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("courseId", "c1", "moduleName", "M1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("quiz-1"));

        verify(moduleQuizService).createSession(isNull(), eq("u1"), any());
    }

    @Test
    void createSessionValidationErrorReturns400() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any())).thenReturn("u1");
        when(moduleQuizService.createSession(isNull(), eq("u1"), any()))
                .thenThrow(new IllegalArgumentException("questions are required"));

        mockMvc.perform(post("/skillama/user-profile/module-quiz/sessions")
                        .header(Constants.AUTH, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("questions are required"));
    }

    @Test
    void createSessionGenerationFailureReturns502WithSkipEligibleFlag() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any())).thenReturn("u1");
        when(moduleQuizService.createSession(isNull(), eq("u1"), any()))
                .thenThrow(new com.prwatech.skillama.exception.QuizGenerationFailedException(
                        "We couldn't generate the quiz right now.", true));

        mockMvc.perform(post("/skillama/user-profile/module-quiz/sessions")
                        .header(Constants.AUTH, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("courseId", "c1", "moduleName", "M1"))))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("We couldn't generate the quiz right now."))
                .andExpect(jsonPath("$.skipEligible").value(true));
    }

    @Test
    void submitAttemptAuthenticatedReturnsResult() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any())).thenReturn("u1");
        when(moduleQuizService.submitAttempt(isNull(), eq("u1"), any()))
                .thenReturn(ModuleQuizAttemptResultDTO.builder()
                        .attemptId("a1").score(2).maxScore(2).percentage(100.0).passed(true).build());

        mockMvc.perform(post("/skillama/user-profile/module-quiz/attempts")
                        .header(Constants.AUTH, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("sessionId", "quiz-1", "answers", Map.of("1", "A")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(true));
    }

    @Test
    void submitAttemptWithoutAuthReturns401() throws Exception {
        mockMvc.perform(post("/skillama/user-profile/module-quiz/attempts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void skipQuizAuthenticatedDelegates() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any())).thenReturn("u1");
        when(moduleQuizService.skipModuleQuiz(isNull(), eq("u1"), eq("c1"), eq("M1")))
                .thenReturn(Map.of("status", "ok", "skipped", true));

        mockMvc.perform(post("/skillama/user-profile/module-quiz/skip")
                        .header(Constants.AUTH, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("courseId", "c1", "moduleName", "M1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skipped").value(true));
    }

    @Test
    void skipQuizBeforeMinAttemptsReturns400() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any())).thenReturn("u1");
        when(moduleQuizService.skipModuleQuiz(isNull(), eq("u1"), eq("c1"), eq("M1")))
                .thenThrow(new IllegalArgumentException("Skip is available after 2 quiz attempts"));

        mockMvc.perform(post("/skillama/user-profile/module-quiz/skip")
                        .header(Constants.AUTH, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("courseId", "c1", "moduleName", "M1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Skip is available after 2 quiz attempts"));
    }
}

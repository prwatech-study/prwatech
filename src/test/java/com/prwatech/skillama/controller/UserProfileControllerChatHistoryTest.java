package com.prwatech.skillama.controller;

import com.prwatech.authentication.security.JwtUtils;
import com.prwatech.common.Constants;
import com.prwatech.skillama.dto.ChatHistoryItemDTO;
import com.prwatech.skillama.model.User;
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

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserProfileControllerChatHistoryTest {

    private MockMvc mockMvc;

    @Mock private UserProfileService userProfileService;
    @Mock private UserService userService;
    @Mock private FreemiumService freemiumService;
    @Mock private ReferralShareService referralShareService;
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
                userProfileService,
                userService,
                freemiumService,
                referralShareService,
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
    void getChatHistory_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/skillama/user-profile/chat/history"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getChatHistory_withBearerToken_returnsCourseScopedHistory() throws Exception {
        User user = User.builder()
                .id("u1")
                .email("learner@skillama.co.in")
                .active(true)
                .build();

        when(skillamaAuthSupport.resolveUserIdFromRequest(any())).thenReturn("u1");
        when(userProfileService.getChatHistory(isNull(), eq("u1"), eq("course-1"), eq(0), eq(20)))
                .thenReturn(List.of(
                        ChatHistoryItemDTO.builder()
                                .id("chat-1")
                                .question("What is Python?")
                                .answer("A programming language.")
                                .courseId("course-1")
                                .questionType("text")
                                .build()));

        mockMvc.perform(get("/skillama/user-profile/chat/history")
                        .param("courseId", "course-1")
                        .header(Constants.AUTH, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].question").value("What is Python?"))
                .andExpect(jsonPath("$[0].answer").value("A programming language."))
                .andExpect(jsonPath("$[0].courseId").value("course-1"));
    }
}

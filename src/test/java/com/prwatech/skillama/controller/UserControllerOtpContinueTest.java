package com.prwatech.skillama.controller;

import com.prwatech.authentication.security.JwtUtils;
import com.prwatech.skillama.dto.OtpContinueRequestDTO;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.service.AdminService;
import com.prwatech.skillama.service.FreemiumService;
import com.prwatech.skillama.service.OAuthAuthService;
import com.prwatech.skillama.service.OnboardingService;
import com.prwatech.skillama.service.OtpService;
import com.prwatech.skillama.service.PasswordResetService;
import com.prwatech.skillama.service.SkillamaAuthSupport;
import com.prwatech.skillama.service.UserContactService;
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

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerOtpContinueTest {

    private MockMvc mockMvc;

    @Mock private UserService userService;
    @Mock private AdminService adminService;
    @Mock private JwtUtils jwtUtils;
    @Mock private OtpService otpService;
    @Mock private FreemiumService freemiumService;
    @Mock private PasswordResetService passwordResetService;
    @Mock private UserContactService userContactService;
    @Mock private OAuthAuthService oAuthAuthService;
    @Mock private OnboardingService onboardingService;
    @Mock private SkillamaAuthSupport skillamaAuthSupport;

    @BeforeEach
    void setUp() {
        UserController controller = new UserController(
                userService,
                adminService,
                jwtUtils,
                otpService,
                freemiumService,
                passwordResetService,
                userContactService,
                oAuthAuthService,
                onboardingService,
                skillamaAuthSupport);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    void otpContinue_newUser_returnsLoginResponseWithToken() throws Exception {
        User created = User.builder()
                .id("u-new")
                .email("new@example.com")
                .name("new")
                .role(User.UserRole.USER)
                .active(true)
                .planTier(User.PlanTier.FREEMIUM)
                .onboardingCompleted(false)
                .build();

        when(oAuthAuthService.otpContinue(any(OtpContinueRequestDTO.class))).thenReturn(created);
        when(jwtUtils.generateToken(any())).thenReturn(Map.of("accessToken", "jwt-token"));
        when(onboardingService.isOnboardingRequired(created)).thenReturn(true);

        mockMvc.perform(post("/skillama/users/auth/otp/continue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"new@example.com","otp":"123456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("u-new"))
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.onboardingRequired").value(true));
    }
}

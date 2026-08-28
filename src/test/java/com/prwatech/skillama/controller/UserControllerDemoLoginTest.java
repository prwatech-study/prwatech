package com.prwatech.skillama.controller;

import com.prwatech.authentication.security.JwtUtils;
import com.prwatech.skillama.dto.DemoOtpSendResultDTO;
import com.prwatech.skillama.dto.LoginResponseDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.exception.SkillamaAuthException;
import com.prwatech.skillama.service.AdminService;
import com.prwatech.skillama.service.DemoAccessService;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Status mapping for the investor-demo one-click login: 200 with the standard
 * LoginResponseDTO, 401 on a bad code, 404 when demo env vars are unset.
 */
@ExtendWith(MockitoExtension.class)
class UserControllerDemoLoginTest {

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
    @Mock private DemoAccessService demoAccessService;

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
                skillamaAuthSupport,
                demoAccessService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    void demoLogin_validOtp_returnsLoginResponse() throws Exception {
        when(demoAccessService.demoLogin("482913")).thenReturn(
                LoginResponseDTO.builder()
                        .id("demo-1")
                        .email("demo@skillama.co.in")
                        .token("demo-jwt")
                        .build());

        mockMvc.perform(post("/skillama/users/demo-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"otp\":\"482913\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("demo-jwt"))
                .andExpect(jsonPath("$.email").value("demo@skillama.co.in"));
    }

    @Test
    void demoLogin_invalidOtp_returns401() throws Exception {
        when(demoAccessService.demoLogin("000000"))
                .thenThrow(new SkillamaAuthException("Invalid or expired code", "DEMO_OTP_INVALID"));

        mockMvc.perform(post("/skillama/users/demo-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"otp\":\"000000\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired code"));
    }

    @Test
    void demoLogin_notConfigured_returns404() throws Exception {
        when(demoAccessService.demoLogin("482913"))
                .thenThrow(new ResourceNotFoundException("Demo access is not configured on this environment"));

        mockMvc.perform(post("/skillama/users/demo-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"otp\":\"482913\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void sendDemoOtp_returnsMaskedEmail() throws Exception {
        when(demoAccessService.sendDemoOtp()).thenReturn(
                DemoOtpSendResultDTO.builder().maskedEmail("o***r@skillama.co.in").build());

        mockMvc.perform(post("/skillama/users/demo-login/otp/send"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maskedEmail").value("o***r@skillama.co.in"));
    }

    @Test
    void sendDemoOtp_cooldown_returns429() throws Exception {
        when(demoAccessService.sendDemoOtp())
                .thenThrow(new IllegalStateException("A code was just sent. Please wait a moment before requesting another."));

        mockMvc.perform(post("/skillama/users/demo-login/otp/send"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void sendDemoOtp_notConfigured_returns404() throws Exception {
        when(demoAccessService.sendDemoOtp())
                .thenThrow(new ResourceNotFoundException("Demo access is not configured on this environment"));

        mockMvc.perform(post("/skillama/users/demo-login/otp/send"))
                .andExpect(status().isNotFound());
    }
}

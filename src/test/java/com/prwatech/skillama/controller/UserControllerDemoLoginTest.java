package com.prwatech.skillama.controller;

import com.prwatech.authentication.security.JwtUtils;
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
    void demoLogin_validCode_returnsLoginResponse() throws Exception {
        when(demoAccessService.demoLogin("pitch-2026")).thenReturn(
                LoginResponseDTO.builder()
                        .id("demo-1")
                        .email("demo@skillama.co.in")
                        .token("demo-jwt")
                        .build());

        mockMvc.perform(post("/skillama/users/demo-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessCode\":\"pitch-2026\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("demo-jwt"))
                .andExpect(jsonPath("$.email").value("demo@skillama.co.in"));
    }

    @Test
    void demoLogin_invalidCode_returns401() throws Exception {
        when(demoAccessService.demoLogin("wrong"))
                .thenThrow(new SkillamaAuthException("Invalid access code", "DEMO_CODE_INVALID"));

        mockMvc.perform(post("/skillama/users/demo-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessCode\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid access code"));
    }

    @Test
    void demoLogin_notConfigured_returns404() throws Exception {
        when(demoAccessService.demoLogin("anything"))
                .thenThrow(new ResourceNotFoundException("Demo access is not configured on this environment"));

        mockMvc.perform(post("/skillama/users/demo-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessCode\":\"anything\"}"))
                .andExpect(status().isNotFound());
    }
}

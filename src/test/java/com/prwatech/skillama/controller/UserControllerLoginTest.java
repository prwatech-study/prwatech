package com.prwatech.skillama.controller;

import com.prwatech.authentication.security.JwtUtils;
import com.prwatech.common.Constants;
import com.prwatech.common.dto.UserDetails;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.service.AdminService;
import com.prwatech.skillama.service.FreemiumService;
import com.prwatech.skillama.service.OtpService;
import com.prwatech.skillama.service.PasswordResetService;
import com.prwatech.skillama.service.UserContactService;
import com.prwatech.skillama.service.OAuthAuthService;
import com.prwatech.skillama.service.OnboardingService;
import com.prwatech.skillama.service.SkillamaAuthSupport;
import com.prwatech.skillama.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression coverage for login — password must deserialize from JSON (WRITE_ONLY, not @JsonIgnore).
 */
@ExtendWith(MockitoExtension.class)
class UserControllerLoginTest {

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

    private static final User ACTIVE_USER = User.builder()
            .id("u1")
            .name("Learner One")
            .email("learner@skillama.co.in")
            .password("bcrypt-hash")
            .role(User.UserRole.USER)
            .active(true)
            .build();

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
    void login_validCredentials_returnsToken() throws Exception {
        when(userService.findByEmail("learner@skillama.co.in")).thenReturn(Optional.of(ACTIVE_USER));
        when(userService.validatePassword("correct-pass", "bcrypt-hash")).thenReturn(true);
        when(jwtUtils.generateToken(any(UserDetails.class)))
                .thenReturn(Map.of("accessToken", "jwt-access-token"));

        mockMvc.perform(post("/skillama/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"email\":\"learner@skillama.co.in\",\"password\":\"correct-pass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-access-token"))
                .andExpect(jsonPath("$.email").value("learner@skillama.co.in"))
                .andExpect(jsonPath("$.password").doesNotExist());

        verify(userService).recordLogin(ACTIVE_USER);
    }

    @Test
    void login_deserializesPasswordFromRequestBody() throws Exception {
        when(userService.findByEmail("learner@skillama.co.in")).thenReturn(Optional.of(ACTIVE_USER));
        when(userService.validatePassword("plain-secret", "bcrypt-hash")).thenReturn(true);
        when(jwtUtils.generateToken(any(UserDetails.class)))
                .thenReturn(Map.of("accessToken", "jwt-access-token"));

        mockMvc.perform(post("/skillama/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"email\":\"learner@skillama.co.in\",\"password\":\"plain-secret\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<String> plainCaptor = ArgumentCaptor.forClass(String.class);
        verify(userService).validatePassword(plainCaptor.capture(), eq("bcrypt-hash"));
        assertEquals("plain-secret", plainCaptor.getValue());
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        when(userService.findByEmail("learner@skillama.co.in")).thenReturn(Optional.of(ACTIVE_USER));
        when(userService.validatePassword("wrong", "bcrypt-hash")).thenReturn(false);

        mockMvc.perform(post("/skillama/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"learner@skillama.co.in\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).recordLogin(any());
        verify(jwtUtils, never()).generateToken(any());
    }

    @Test
    void login_unknownEmail_returns401() throws Exception {
        when(userService.findByEmail("missing@skillama.co.in")).thenReturn(Optional.empty());

        mockMvc.perform(post("/skillama/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"missing@skillama.co.in\",\"password\":\"any\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_inactiveAccount_returns403() throws Exception {
        User inactive = User.builder()
                .id("u2")
                .email("inactive@skillama.co.in")
                .password("hash")
                .active(false)
                .build();
        when(userService.findByEmail("inactive@skillama.co.in")).thenReturn(Optional.of(inactive));

        mockMvc.perform(post("/skillama/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"inactive@skillama.co.in\",\"password\":\"pass\"}"))
                .andExpect(status().isForbidden());

        verify(userService, never()).validatePassword(any(), any());
    }
}

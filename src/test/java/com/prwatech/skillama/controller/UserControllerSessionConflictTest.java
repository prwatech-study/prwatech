package com.prwatech.skillama.controller;

import com.prwatech.authentication.security.JwtUtils;
import com.prwatech.common.Constants;
import com.prwatech.common.dto.UserDetails;
import com.prwatech.skillama.exception.SkillamaAuthException;
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

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Single-active-session enforcement at the login endpoints: a second login while a session
 * is already active must conflict (409) unless forceLogin=true is passed, matching the
 * "show a popup, sign out the other device" product flow.
 */
@ExtendWith(MockitoExtension.class)
class UserControllerSessionConflictTest {

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

    private static final LocalDateTime LAST_LOGIN = LocalDateTime.of(2026, 8, 1, 9, 30);

    private static User activeSessionUser() {
        return User.builder()
                .id("u1")
                .email("learner@skillama.co.in")
                .password("bcrypt-hash")
                .role(User.UserRole.USER)
                .active(true)
                .sessionActive(true)
                .lastLoginAt(LAST_LOGIN)
                .tokenVersion(4)
                .build();
    }

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
    void login_activeSessionElsewhere_returns409ConflictWithoutMintingToken() throws Exception {
        User user = activeSessionUser();
        when(userService.findByEmail("learner@skillama.co.in")).thenReturn(Optional.of(user));
        when(userService.validatePassword("correct-pass", "bcrypt-hash")).thenReturn(true);

        mockMvc.perform(post("/skillama/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"learner@skillama.co.in\",\"password\":\"correct-pass\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("conflict"))
                // Regression: must be an ISO string (new Date(x) on the frontend), not the raw
                // LocalDateTime — this app's ObjectMapper serializes that as a component array.
                .andExpect(jsonPath("$.lastLoginAt").value(LAST_LOGIN.toString()));

        verify(userService, never()).startNewSession(any());
        verify(jwtUtils, never()).generateToken(any(), any());
    }

    @Test
    void login_forceLogin_bypassesConflictAndStartsNewSession() throws Exception {
        User user = activeSessionUser();
        when(userService.findByEmail("learner@skillama.co.in")).thenReturn(Optional.of(user));
        when(userService.validatePassword("correct-pass", "bcrypt-hash")).thenReturn(true);
        when(userService.startNewSession("u1")).thenReturn(5);
        when(jwtUtils.generateToken(any(UserDetails.class), org.mockito.ArgumentMatchers.eq(5)))
                .thenReturn(Map.of("accessToken", "jwt-access-token"));

        mockMvc.perform(post("/skillama/users/login")
                        .param("forceLogin", "true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"learner@skillama.co.in\",\"password\":\"correct-pass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-access-token"));

        verify(userService).startNewSession("u1");
    }

    @Test
    void login_noActiveSession_succeedsWithoutConflict() throws Exception {
        User user = User.builder()
                .id("u1")
                .email("learner@skillama.co.in")
                .password("bcrypt-hash")
                .active(true)
                .sessionActive(false)
                .build();
        when(userService.findByEmail("learner@skillama.co.in")).thenReturn(Optional.of(user));
        when(userService.validatePassword("correct-pass", "bcrypt-hash")).thenReturn(true);
        when(userService.startNewSession("u1")).thenReturn(1);
        when(jwtUtils.generateToken(any(UserDetails.class), any()))
                .thenReturn(Map.of("accessToken", "jwt-access-token"));

        mockMvc.perform(post("/skillama/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"learner@skillama.co.in\",\"password\":\"correct-pass\"}"))
                .andExpect(status().isOk());

        verify(userService).startNewSession("u1");
    }

    @Test
    void loginOtp_activeSessionElsewhere_returns409Conflict() throws Exception {
        User user = activeSessionUser();
        when(userService.findByEmail("learner@skillama.co.in")).thenReturn(Optional.of(user));
        when(otpService.verifyOtp("learner@skillama.co.in", "123456")).thenReturn(null);

        mockMvc.perform(post("/skillama/users/login/otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"learner@skillama.co.in\",\"otp\":\"123456\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("conflict"));

        verify(userService, never()).startNewSession(any());
    }

    @Test
    void authGoogle_activeSessionElsewhere_returns409ConflictWithoutMintingToken() throws Exception {
        User user = activeSessionUser();
        when(oAuthAuthService.authenticateWithGoogle(any())).thenReturn(user);

        mockMvc.perform(post("/skillama/users/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"google-id-token\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("conflict"));

        verify(userService, never()).recordLogin(any());
        verify(jwtUtils, never()).generateToken(any(), any());
    }

    @Test
    void authGoogle_forceLogin_bypassesConflict() throws Exception {
        User user = activeSessionUser();
        when(oAuthAuthService.authenticateWithGoogle(any())).thenReturn(user);
        when(userService.startNewSession("u1")).thenReturn(5);
        when(jwtUtils.generateToken(any(UserDetails.class), org.mockito.ArgumentMatchers.eq(5)))
                .thenReturn(Map.of("accessToken", "jwt-access-token"));

        mockMvc.perform(post("/skillama/users/auth/google")
                        .param("forceLogin", "true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"google-id-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-access-token"));

        verify(userService).startNewSession("u1");
    }

    @Test
    void logout_revokesCurrentTokenServerSide() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any())).thenReturn("u1");

        mockMvc.perform(post("/skillama/users/logout")
                        .header(Constants.AUTH, "Bearer some-jwt-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        verify(userService).logout("u1");
    }

    @Test
    void logout_invalidToken_returns401WithoutCallingLogout() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any()))
                .thenThrow(new SkillamaAuthException("Session expired. Please sign in again."));

        mockMvc.perform(post("/skillama/users/logout")
                        .header(Constants.AUTH, "Bearer bad-token"))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).logout(any());
    }

    @Test
    void session_revokedElsewhere_returns401WithReasonForPolling() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any()))
                .thenThrow(new SkillamaAuthException(
                        "You've been signed out because this account was signed in elsewhere.",
                        "SESSION_REVOKED"));

        mockMvc.perform(get("/skillama/users/session")
                        .header(Constants.AUTH, "Bearer stale-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.reason").value("SESSION_REVOKED"));
    }

    // --- Heartbeat (active-time tracking) ---

    @Test
    void sessionHeartbeat_activeSession_returnsActiveTrue() throws Exception {
        when(skillamaAuthSupport.resolveSessionFromRequest(any()))
                .thenReturn(new SkillamaAuthSupport.ResolvedSession("u1", 4));
        when(userService.recordHeartbeat("u1", 4)).thenReturn(true);

        mockMvc.perform(post("/skillama/users/session/heartbeat")
                        .header(Constants.AUTH, "Bearer some-jwt-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void sessionHeartbeat_sessionReplacedElsewhere_returns200WithActiveFalse() throws Exception {
        // The revocation poll (separate mechanism) is what actually signs the user out —
        // the heartbeat itself just reports whether this specific login is still the live one.
        when(skillamaAuthSupport.resolveSessionFromRequest(any()))
                .thenReturn(new SkillamaAuthSupport.ResolvedSession("u1", 2));
        when(userService.recordHeartbeat("u1", 2)).thenReturn(false);

        mockMvc.perform(post("/skillama/users/session/heartbeat")
                        .header(Constants.AUTH, "Bearer stale-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void sessionHeartbeat_invalidToken_returns401() throws Exception {
        when(skillamaAuthSupport.resolveSessionFromRequest(any()))
                .thenThrow(new SkillamaAuthException("Session expired. Please sign in again."));

        mockMvc.perform(post("/skillama/users/session/heartbeat")
                        .header(Constants.AUTH, "Bearer bad-token"))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).recordHeartbeat(any(), anyInt());
    }

    // --- Active-time aggregation (billing) ---

    @Test
    void getUserActiveTime_self_returnsSeconds() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any())).thenReturn("u1");
        when(userService.findById("u1")).thenReturn(Optional.of(activeSessionUser()));
        when(userService.getTotalActiveSeconds("u1",
                LocalDateTime.parse("2026-08-01T00:00:00"), LocalDateTime.parse("2026-09-01T00:00:00")))
                .thenReturn(3600L);

        mockMvc.perform(get("/skillama/users/u1/active-time")
                        .param("from", "2026-08-01T00:00:00")
                        .param("to", "2026-09-01T00:00:00")
                        .header(Constants.AUTH, "Bearer some-jwt-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeSeconds").value(3600));
    }

    @Test
    void getUserActiveTime_otherLearnerRequestingSomeoneElses_returns403() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any())).thenReturn("requester-id");
        when(userService.findById("requester-id")).thenReturn(Optional.of(
                User.builder().id("requester-id").role(User.UserRole.USER).build()));

        mockMvc.perform(get("/skillama/users/u1/active-time")
                        .param("from", "2026-08-01T00:00:00")
                        .param("to", "2026-09-01T00:00:00")
                        .header(Constants.AUTH, "Bearer some-jwt-token"))
                .andExpect(status().isForbidden());

        verify(userService, never()).getTotalActiveSeconds(any(), any(), any());
    }

    @Test
    void getUserActiveTime_malformedDate_returns400WithoutCallingAggregation() throws Exception {
        mockMvc.perform(get("/skillama/users/u1/active-time")
                        .param("from", "not-a-date")
                        .param("to", "2026-09-01T00:00:00")
                        .header(Constants.AUTH, "Bearer some-jwt-token"))
                .andExpect(status().isBadRequest());

        verify(userService, never()).getTotalActiveSeconds(any(), any(), any());
        verify(skillamaAuthSupport, never()).resolveUserIdFromRequest(any());
    }

    @Test
    void getUserActiveTime_adminRequestingAnotherUser_returnsSeconds() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any())).thenReturn("admin-id");
        when(userService.findById("admin-id")).thenReturn(Optional.of(
                User.builder().id("admin-id").role(User.UserRole.ADMIN).build()));
        when(userService.getTotalActiveSeconds(eq("u1"), any(), any())).thenReturn(7200L);

        mockMvc.perform(get("/skillama/users/u1/active-time")
                        .param("from", "2026-08-01T00:00:00")
                        .param("to", "2026-09-01T00:00:00")
                        .header(Constants.AUTH, "Bearer admin-jwt-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeSeconds").value(7200));
    }
}

package com.prwatech.skillama.service;

import com.prwatech.authentication.security.JwtUtils;
import com.prwatech.common.dto.UserDetails;
import com.prwatech.skillama.dto.LoginResponseDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.exception.SkillamaAuthException;
import com.prwatech.skillama.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoAccessServiceTest {

    private static final String CODE = "pitch-2026";
    private static final String DEMO_EMAIL = "demo@skillama.co.in";

    @Mock private UserService userService;
    @Mock private JwtUtils jwtUtils;
    @Mock private OnboardingService onboardingService;

    private DemoAccessService service() {
        return service(CODE, DEMO_EMAIL);
    }

    private DemoAccessService service(String accessCode, String email) {
        return new DemoAccessService(userService, jwtUtils, onboardingService, accessCode, email);
    }

    private User demoUser() {
        return User.builder()
                .id("demo-1")
                .name("Demo Learner")
                .email(DEMO_EMAIL)
                .role(User.UserRole.USER)
                .active(true)
                .build();
    }

    @Test
    void demoLogin_correctCode_startsNewSessionAndReturnsLoginResponse() {
        User user = demoUser();
        when(userService.findByEmail(DEMO_EMAIL)).thenReturn(Optional.of(user));
        when(userService.startNewSession("demo-1")).thenReturn(7);
        when(jwtUtils.generateToken(any(UserDetails.class), anyInt()))
                .thenReturn(Map.of("accessToken", "demo-jwt"));

        LoginResponseDTO response = service().demoLogin(CODE);

        assertEquals("demo-jwt", response.getToken());
        assertEquals(DEMO_EMAIL, response.getEmail());
        verify(userService).recordLogin(user);
        verify(userService).startNewSession("demo-1");
    }

    @Test
    void demoLogin_forceSemantics_activeSessionElsewhereStillSucceeds() {
        User user = demoUser();
        user.setSessionActive(true);
        when(userService.findByEmail(DEMO_EMAIL)).thenReturn(Optional.of(user));
        when(userService.startNewSession("demo-1")).thenReturn(8);
        when(jwtUtils.generateToken(any(UserDetails.class), anyInt()))
                .thenReturn(Map.of("accessToken", "demo-jwt-2"));

        LoginResponseDTO response = service().demoLogin(CODE);

        assertEquals("demo-jwt-2", response.getToken());
        verify(userService).startNewSession("demo-1");
    }

    @Test
    void demoLogin_wrongCode_throwsAuthAndNeverTouchesSession() {
        SkillamaAuthException e =
                assertThrows(SkillamaAuthException.class, () -> service().demoLogin("nope"));
        assertEquals("DEMO_CODE_INVALID", e.getReason());
        verify(userService, never()).recordLogin(any());
        verify(userService, never()).startNewSession(any());
    }

    @Test
    void demoLogin_nullCode_throwsAuth() {
        assertThrows(SkillamaAuthException.class, () -> service().demoLogin(null));
    }

    @Test
    void demoLogin_blankConfig_throwsNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service("", DEMO_EMAIL).demoLogin(CODE));
        assertThrows(ResourceNotFoundException.class, () -> service(CODE, "").demoLogin(CODE));
    }

    @Test
    void demoLogin_inactiveUser_rejected() {
        User user = demoUser();
        user.setActive(false);
        when(userService.findByEmail(DEMO_EMAIL)).thenReturn(Optional.of(user));

        SkillamaAuthException e =
                assertThrows(SkillamaAuthException.class, () -> service().demoLogin(CODE));
        assertEquals("DEMO_ACCOUNT_INACTIVE", e.getReason());
        verify(userService, never()).startNewSession(any());
    }

    @Test
    void demoLogin_adminOrOwnerTarget_neverMintsToken() {
        for (User.UserRole role : new User.UserRole[] {User.UserRole.ADMIN, User.UserRole.OWNER}) {
            User user = demoUser();
            user.setRole(role);
            when(userService.findByEmail(DEMO_EMAIL)).thenReturn(Optional.of(user));

            SkillamaAuthException e =
                    assertThrows(SkillamaAuthException.class, () -> service().demoLogin(CODE));
            assertEquals("DEMO_ACCOUNT_INVALID", e.getReason());
        }
        verify(userService, never()).startNewSession(any());
        verify(jwtUtils, never()).generateToken(any(UserDetails.class), anyInt());
    }

    @Test
    void demoLogin_tenFailures_locksOutEvenCorrectCode() {
        DemoAccessService service = service();
        for (int i = 0; i < 10; i++) {
            assertThrows(SkillamaAuthException.class, () -> service.demoLogin("wrong"));
        }
        SkillamaAuthException e =
                assertThrows(SkillamaAuthException.class, () -> service.demoLogin(CODE));
        assertEquals("DEMO_LOCKED", e.getReason());
    }
}

package com.prwatech.skillama.service;

import com.prwatech.authentication.security.JwtUtils;
import com.prwatech.common.dto.UserDetails;
import com.prwatech.skillama.dto.DemoOtpSendResultDTO;
import com.prwatech.skillama.dto.LoginResponseDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.exception.SkillamaAuthException;
import com.prwatech.skillama.model.EmailOtp;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoAccessServiceTest {

    private static final String OTP = "482913";
    private static final String OWNER_EMAIL = "owner@skillama.co.in";
    private static final String DEMO_EMAIL = "demo@skillama.co.in";

    @Mock private UserService userService;
    @Mock private JwtUtils jwtUtils;
    @Mock private OnboardingService onboardingService;
    @Mock private OtpService otpService;

    private DemoAccessService service() {
        return service(OWNER_EMAIL, DEMO_EMAIL);
    }

    private DemoAccessService service(String otpEmail, String email) {
        return new DemoAccessService(
                userService, jwtUtils, onboardingService, otpService, otpEmail, email);
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
    void sendDemoOtp_sendsToOwnerAndReturnsMaskedEmail() {
        DemoOtpSendResultDTO result = service().sendDemoOtp();

        verify(otpService).sendOtp(OWNER_EMAIL, EmailOtp.OtpPurpose.DEMO_LOGIN);
        assertEquals("o***r@skillama.co.in", result.getMaskedEmail());
    }

    @Test
    void sendDemoOtp_secondImmediateSend_hitsCooldown() {
        DemoAccessService service = service();
        service.sendDemoOtp();

        assertThrows(IllegalStateException.class, service::sendDemoOtp);
        verify(otpService, times(1)).sendOtp(anyString(), any());
    }

    @Test
    void sendDemoOtp_blankConfig_throwsNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service("", DEMO_EMAIL).sendDemoOtp());
        assertThrows(ResourceNotFoundException.class, () -> service(OWNER_EMAIL, "").sendDemoOtp());
        verify(otpService, never()).sendOtp(anyString(), any());
    }

    @Test
    void demoLogin_validOtp_startsNewSessionAndReturnsLoginResponse() {
        User user = demoUser();
        when(userService.findByEmail(DEMO_EMAIL)).thenReturn(Optional.of(user));
        when(userService.startNewSession("demo-1")).thenReturn(7);
        when(jwtUtils.generateToken(any(UserDetails.class), anyInt()))
                .thenReturn(Map.of("accessToken", "demo-jwt"));

        LoginResponseDTO response = service().demoLogin(OTP);

        verify(otpService).verifyOtp(OWNER_EMAIL, OTP, EmailOtp.OtpPurpose.DEMO_LOGIN);
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

        LoginResponseDTO response = service().demoLogin(OTP);

        assertEquals("demo-jwt-2", response.getToken());
        verify(userService).startNewSession("demo-1");
    }

    @Test
    void demoLogin_invalidOtp_throwsAuthAndNeverTouchesSession() {
        doThrow(new IllegalArgumentException("Invalid OTP"))
                .when(otpService).verifyOtp(OWNER_EMAIL, "nope", EmailOtp.OtpPurpose.DEMO_LOGIN);

        SkillamaAuthException e =
                assertThrows(SkillamaAuthException.class, () -> service().demoLogin("nope"));
        assertEquals("DEMO_OTP_INVALID", e.getReason());
        verify(userService, never()).recordLogin(any());
        verify(userService, never()).startNewSession(any());
    }

    @Test
    void demoLogin_blankOtp_rejectedWithoutVerifying() {
        SkillamaAuthException e =
                assertThrows(SkillamaAuthException.class, () -> service().demoLogin("  "));
        assertEquals("DEMO_OTP_INVALID", e.getReason());
        assertThrows(SkillamaAuthException.class, () -> service().demoLogin(null));
        verify(otpService, never()).verifyOtp(anyString(), anyString(), any());
    }

    @Test
    void demoLogin_blankConfig_throwsNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service("", DEMO_EMAIL).demoLogin(OTP));
        assertThrows(ResourceNotFoundException.class, () -> service(OWNER_EMAIL, "").demoLogin(OTP));
    }

    @Test
    void demoLogin_inactiveUser_rejected() {
        User user = demoUser();
        user.setActive(false);
        when(userService.findByEmail(DEMO_EMAIL)).thenReturn(Optional.of(user));

        SkillamaAuthException e =
                assertThrows(SkillamaAuthException.class, () -> service().demoLogin(OTP));
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
                    assertThrows(SkillamaAuthException.class, () -> service().demoLogin(OTP));
            assertEquals("DEMO_ACCOUNT_INVALID", e.getReason());
        }
        verify(userService, never()).startNewSession(any());
        verify(jwtUtils, never()).generateToken(any(UserDetails.class), anyInt());
    }

    @Test
    void demoLogin_tenFailures_locksOutEvenValidOtp() {
        DemoAccessService service = service();
        doThrow(new IllegalArgumentException("Invalid OTP"))
                .when(otpService).verifyOtp(OWNER_EMAIL, "wrong", EmailOtp.OtpPurpose.DEMO_LOGIN);
        for (int i = 0; i < 10; i++) {
            assertThrows(SkillamaAuthException.class, () -> service.demoLogin("wrong"));
        }

        SkillamaAuthException e =
                assertThrows(SkillamaAuthException.class, () -> service.demoLogin(OTP));
        assertEquals("DEMO_LOCKED", e.getReason());
    }

    @Test
    void maskEmail_masksLocalPart() {
        assertEquals("o***r@skillama.co.in", DemoAccessService.maskEmail(OWNER_EMAIL));
        assertEquals("***@x.com", DemoAccessService.maskEmail("a@x.com"));
    }
}

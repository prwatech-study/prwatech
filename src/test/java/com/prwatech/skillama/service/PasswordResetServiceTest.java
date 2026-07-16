package com.prwatech.skillama.service;

import com.prwatech.common.configuration.PasswordEncode;
import com.prwatech.skillama.dto.ResetPasswordRequestDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.EmailOtp;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PasswordResetServiceTest {

    @Mock private SkillamaUserRepository userRepository;
    @Mock private OtpService otpService;
    @Mock private PasswordEncode passwordEncode;

    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(userRepository, otpService, passwordEncode);
        when(passwordEncode.getEncryptedPassword(any())).thenReturn("new-hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private ResetPasswordRequestDTO request() {
        ResetPasswordRequestDTO r = new ResetPasswordRequestDTO();
        r.setEmail("u@x.com");
        r.setNewPassword("secret123");
        return r;
    }

    // ---------- forgot password (no enumeration) ----------

    @Test
    void forgotPasswordRejectsBlankEmail() {
        assertThrows(IllegalArgumentException.class, () -> service.sendForgotPasswordOtp("  "));
    }

    @Test
    void forgotPasswordSendsOtpWhenUserExists() {
        when(userRepository.findByEmail("u@x.com")).thenReturn(Optional.of(User.builder().id("u1").build()));
        service.sendForgotPasswordOtp("U@X.com");
        verify(otpService).sendOtp("u@x.com", EmailOtp.OtpPurpose.PASSWORD_RESET);
    }

    @Test
    void forgotPasswordSilentlySucceedsForUnknownEmail() {
        when(userRepository.findByEmail("u@x.com")).thenReturn(Optional.empty());
        service.sendForgotPasswordOtp("u@x.com"); // no throw — avoids account enumeration
        verify(otpService, never()).sendOtp(any(), any());
    }

    // ---------- reset ----------

    @Test
    void resetRejectsBlankEmail() {
        ResetPasswordRequestDTO r = request();
        r.setEmail(" ");
        assertThrows(IllegalArgumentException.class, () -> service.resetPassword(r));
    }

    @Test
    void resetRejectsShortPassword() {
        ResetPasswordRequestDTO r = request();
        r.setNewPassword("123");
        assertThrows(IllegalArgumentException.class, () -> service.resetPassword(r));
    }

    @Test
    void resetUnknownUserThrows() {
        when(userRepository.findByEmail("u@x.com")).thenReturn(Optional.empty());
        ResetPasswordRequestDTO r = request();
        r.setOtp("123456");
        assertThrows(ResourceNotFoundException.class, () -> service.resetPassword(r));
    }

    @Test
    void resetRequiresOtpOrToken() {
        when(userRepository.findByEmail("u@x.com")).thenReturn(Optional.of(User.builder().id("u1").build()));
        assertThrows(IllegalArgumentException.class, () -> service.resetPassword(request()));
    }

    @Test
    void resetWithVerificationTokenValidatesAndUpdatesPassword() {
        User user = User.builder().id("u1").email("u@x.com").password("old").build();
        when(userRepository.findByEmail("u@x.com")).thenReturn(Optional.of(user));
        ResetPasswordRequestDTO r = request();
        r.setVerificationToken("tok");

        service.resetPassword(r);

        verify(otpService).validateVerificationToken("u@x.com", "tok", EmailOtp.OtpPurpose.PASSWORD_RESET);
        assertEquals("new-hash", user.getPassword());
        verify(userRepository).save(user);
    }

    @Test
    void resetWithOtpVerifiesAndUpdatesPassword() {
        User user = User.builder().id("u1").email("u@x.com").password("old").build();
        when(userRepository.findByEmail("u@x.com")).thenReturn(Optional.of(user));
        ResetPasswordRequestDTO r = request();
        r.setOtp("123456");

        service.resetPassword(r);

        verify(otpService).verifyOtp("u@x.com", "123456", EmailOtp.OtpPurpose.PASSWORD_RESET);
        assertEquals("new-hash", user.getPassword());
    }

    @Test
    void resetPropagatesInvalidOtp() {
        User user = User.builder().id("u1").email("u@x.com").build();
        when(userRepository.findByEmail("u@x.com")).thenReturn(Optional.of(user));
        when(otpService.verifyOtp(eq("u@x.com"), eq("bad"), eq(EmailOtp.OtpPurpose.PASSWORD_RESET)))
                .thenThrow(new IllegalArgumentException("Invalid OTP"));
        ResetPasswordRequestDTO r = request();
        r.setOtp("bad");

        assertThrows(IllegalArgumentException.class, () -> service.resetPassword(r));
        verify(userRepository, never()).save(any());
    }
}

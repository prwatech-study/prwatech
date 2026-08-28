package com.prwatech.skillama.service;

import com.prwatech.authentication.security.JwtUtils;
import com.prwatech.common.dto.UserDetails;
import com.prwatech.skillama.dto.DemoOtpSendResultDTO;
import com.prwatech.skillama.dto.LoginResponseDTO;
import com.prwatech.skillama.dto.UserMapper;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.exception.SkillamaAuthException;
import com.prwatech.skillama.model.EmailOtp;
import com.prwatech.skillama.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * OTP-gated login for the shared investor-demo learner account. The one-time
 * code is emailed to the owner ({@code skillama.demo.otp-email}); whoever the
 * owner shares it with can enter the demo. Disabled (404) unless both
 * {@code skillama.demo.otp-email} and {@code skillama.demo.user-email} are
 * non-empty. Defaults live in application.properties; the
 * SKILLAMA_DEMO_OTP_EMAIL / SKILLAMA_DEMO_USER_EMAIL env vars override them
 * per environment (set either to empty to disable the demo there).
 */
@Service
public class DemoAccessService {

    private static final int MAX_FAILED_ATTEMPTS = 10;
    private static final long LOCKOUT_MS = 60_000L;
    /** Both send endpoints are unauthenticated; don't let them spam the owner's inbox. */
    private static final long SEND_COOLDOWN_MS = 30_000L;

    private final UserService userService;
    private final JwtUtils jwtUtils;
    private final OnboardingService onboardingService;
    private final OtpService otpService;
    private final String otpEmail;
    private final String demoUserEmail;

    private final AtomicInteger failedAttempts = new AtomicInteger();
    private volatile long lockedUntilMs;
    private volatile long lastOtpSentAtMs;

    public DemoAccessService(
            UserService userService,
            JwtUtils jwtUtils,
            OnboardingService onboardingService,
            OtpService otpService,
            @Value("${skillama.demo.otp-email:}") String otpEmail,
            @Value("${skillama.demo.user-email:}") String demoUserEmail) {
        this.userService = userService;
        this.jwtUtils = jwtUtils;
        this.onboardingService = onboardingService;
        this.otpService = otpService;
        this.otpEmail = otpEmail;
        this.demoUserEmail = demoUserEmail;
    }

    /** Email a one-time code to the owner. Returns the masked recipient for the UI. */
    public DemoOtpSendResultDTO sendDemoOtp() {
        assertConfigured();
        long now = System.currentTimeMillis();
        if (now - lastOtpSentAtMs < SEND_COOLDOWN_MS) {
            throw new IllegalStateException("A code was just sent. Please wait a moment before requesting another.");
        }
        lastOtpSentAtMs = now;
        otpService.sendOtp(otpEmail, EmailOtp.OtpPurpose.DEMO_LOGIN);
        return DemoOtpSendResultDTO.builder()
                .maskedEmail(maskEmail(otpEmail))
                .build();
    }

    public LoginResponseDTO demoLogin(String suppliedOtp) {
        assertConfigured();

        long now = System.currentTimeMillis();
        if (now < lockedUntilMs) {
            throw new SkillamaAuthException(
                    "Too many failed attempts. Try again in a minute.", "DEMO_LOCKED");
        }

        if (!StringUtils.hasText(suppliedOtp)) {
            throw new SkillamaAuthException("Enter the one-time code", "DEMO_OTP_INVALID");
        }
        try {
            otpService.verifyOtp(otpEmail, suppliedOtp.trim(), EmailOtp.OtpPurpose.DEMO_LOGIN);
        } catch (IllegalArgumentException e) {
            if (failedAttempts.incrementAndGet() >= MAX_FAILED_ATTEMPTS) {
                lockedUntilMs = now + LOCKOUT_MS;
                failedAttempts.set(0);
            }
            throw new SkillamaAuthException("Invalid or expired code", "DEMO_OTP_INVALID");
        }
        failedAttempts.set(0);

        User user = userService.findByEmail(demoUserEmail)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Demo user not found: " + demoUserEmail));
        if (!user.isActive()) {
            throw new SkillamaAuthException("Demo account is not activated", "DEMO_ACCOUNT_INACTIVE");
        }
        // An emailed code must never mint an admin token, even if the env var points at one.
        if (user.getRole() == User.UserRole.ADMIN || user.getRole() == User.UserRole.OWNER) {
            throw new SkillamaAuthException(
                    "Demo login applies to learner (USER) accounts only", "DEMO_ACCOUNT_INVALID");
        }

        // Force-login semantics: always start a new session so a stale session on
        // another device never blocks a pitch (older session gets SESSION_REVOKED).
        userService.recordLogin(user);
        UserDetails userDetails = new UserDetails(user.getEmail());
        int sessionVersion = userService.startNewSession(user.getId());
        String accessToken = jwtUtils.generateToken(userDetails, sessionVersion).get("accessToken");
        return UserMapper.toLoginResponse(user, accessToken, onboardingService);
    }

    private void assertConfigured() {
        if (!StringUtils.hasText(otpEmail) || !StringUtils.hasText(demoUserEmail)) {
            throw new ResourceNotFoundException("Demo access is not configured on this environment");
        }
    }

    static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + (at >= 0 ? email.substring(at) : "");
        }
        return email.charAt(0) + "***" + email.substring(at - 1);
    }
}

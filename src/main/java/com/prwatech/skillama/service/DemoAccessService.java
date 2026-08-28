package com.prwatech.skillama.service;

import com.prwatech.authentication.security.JwtUtils;
import com.prwatech.common.dto.UserDetails;
import com.prwatech.skillama.dto.LoginResponseDTO;
import com.prwatech.skillama.dto.UserMapper;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.exception.SkillamaAuthException;
import com.prwatech.skillama.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One-click login for the shared investor-demo learner account, gated by a
 * server-side access code. Disabled (404) unless both
 * {@code skillama.demo.access-code} and {@code skillama.demo.user-email} are set,
 * so the endpoint is inert on environments without demo env vars.
 */
@Service
public class DemoAccessService {

    private static final int MAX_FAILED_ATTEMPTS = 10;
    private static final long LOCKOUT_MS = 60_000L;

    private final UserService userService;
    private final JwtUtils jwtUtils;
    private final OnboardingService onboardingService;
    private final String accessCode;
    private final String demoUserEmail;

    private final AtomicInteger failedAttempts = new AtomicInteger();
    private volatile long lockedUntilMs;

    public DemoAccessService(
            UserService userService,
            JwtUtils jwtUtils,
            OnboardingService onboardingService,
            @Value("${skillama.demo.access-code:}") String accessCode,
            @Value("${skillama.demo.user-email:}") String demoUserEmail) {
        this.userService = userService;
        this.jwtUtils = jwtUtils;
        this.onboardingService = onboardingService;
        this.accessCode = accessCode;
        this.demoUserEmail = demoUserEmail;
    }

    public LoginResponseDTO demoLogin(String suppliedCode) {
        if (!StringUtils.hasText(accessCode) || !StringUtils.hasText(demoUserEmail)) {
            throw new ResourceNotFoundException("Demo access is not configured on this environment");
        }

        long now = System.currentTimeMillis();
        if (now < lockedUntilMs) {
            throw new SkillamaAuthException(
                    "Too many failed attempts. Try again in a minute.", "DEMO_LOCKED");
        }

        byte[] expected = accessCode.getBytes(StandardCharsets.UTF_8);
        byte[] supplied = suppliedCode == null
                ? new byte[0]
                : suppliedCode.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, supplied)) {
            if (failedAttempts.incrementAndGet() >= MAX_FAILED_ATTEMPTS) {
                lockedUntilMs = now + LOCKOUT_MS;
                failedAttempts.set(0);
            }
            throw new SkillamaAuthException("Invalid access code", "DEMO_CODE_INVALID");
        }
        failedAttempts.set(0);

        User user = userService.findByEmail(demoUserEmail)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Demo user not found: " + demoUserEmail));
        if (!user.isActive()) {
            throw new SkillamaAuthException("Demo account is not activated", "DEMO_ACCOUNT_INACTIVE");
        }
        // A shared code must never mint an admin token, even if the env var points at one.
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
}

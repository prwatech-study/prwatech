package com.prwatech.skillama.controller;

import com.prwatech.authentication.security.JwtUtils;
import com.prwatech.common.dto.UserDetails;
import com.prwatech.skillama.dto.*;
import com.prwatech.skillama.model.EmailOtp;
import com.prwatech.skillama.model.User;
import com.prwatech.common.Constants;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.exception.SkillamaAuthException;
import com.prwatech.skillama.service.AdminService;
import com.prwatech.skillama.service.FreemiumService;
import com.prwatech.skillama.service.OAuthAuthService;
import com.prwatech.skillama.service.OnboardingService;
import com.prwatech.skillama.service.OtpService;
import com.prwatech.skillama.service.PasswordResetService;
import com.prwatech.skillama.service.SkillamaAuthSupport;
import com.prwatech.skillama.service.UserContactService;
import com.prwatech.skillama.service.UserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController("skillamaUserController")
@RequestMapping("/skillama/users")
@RequiredArgsConstructor
public class UserController {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;
    private final AdminService adminService;
    private final JwtUtils jwtUtils;
    private final OtpService otpService;
    private final FreemiumService freemiumService;
    private final PasswordResetService passwordResetService;
    private final UserContactService userContactService;
    private final OAuthAuthService oAuthAuthService;
    private final OnboardingService onboardingService;
    private final SkillamaAuthSupport skillamaAuthSupport;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user) {
        try {
            userContactService.assertContactUnique(user.getEmail(), user.getPhone(), null);
            if (user.getEmail() != null) {
                user.setEmail(userContactService.normalizeEmail(user.getEmail()));
            }
            return ResponseEntity.ok(userService.register(user));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of(
                    "status", "error",
                    "message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody User loginRequest,
            @RequestParam(defaultValue = "false") boolean forceLogin) {
        Optional<User> userOpt = userService.findByEmail(loginRequest.getEmail());
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (!user.isActive()) {
                return ResponseEntity.status(403).body("Account is not activated. Please contact admin.");
            }
            // Password comparison: passwords are stored encoded in DB
            if (userService.validatePassword(loginRequest.getPassword(), user.getPassword())) {
                if (hasConflictingSession(user, forceLogin)) {
                    return sessionConflictResponse(user);
                }
                userService.recordLogin(user);
                UserDetails userDetails = new UserDetails(user.getEmail());
                int sessionVersion = userService.startNewSession(user.getId());
                Map<String, String> tokens = jwtUtils.generateToken(userDetails, sessionVersion);
                String accessToken = tokens.get("accessToken");

                LoginResponseDTO response = UserMapper.toLoginResponse(user, accessToken, onboardingService);

                return ResponseEntity.ok(response);
            }
        }
        return ResponseEntity.status(401).body("Invalid credentials");
    }

    /** Only a genuinely new login (not a retry with the user's own confirmation) should conflict. */
    private boolean hasConflictingSession(User user, boolean forceLogin) {
        return !forceLogin && Boolean.TRUE.equals(user.getSessionActive());
    }

    private ResponseEntity<?> sessionConflictResponse(User user) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", "conflict");
        body.put("message", "You're already logged in on another device.");
        // .toString() (ISO-8601), not the raw LocalDateTime: this app's ObjectMapper serializes
        // LocalDateTime as a [year,month,day,...] component array, which JS Date can't parse.
        body.put("lastLoginAt", user.getLastLoginAt() != null ? user.getLastLoginAt().toString() : null);
        return ResponseEntity.status(409).body(body);
    }

    @PostMapping("/otp/email/send")
    public ResponseEntity<?> sendEmailOtp(@RequestBody OtpSendRequestDTO request) {
        try {
            EmailOtp.OtpPurpose purpose = request.getPurpose() != null ? request.getPurpose() : EmailOtp.OtpPurpose.SIGNUP;
            if (purpose == EmailOtp.OtpPurpose.SIGNUP) {
                otpService.assertSignupContactAvailable(request.getEmail(), request.getPhone());
            }
            otpService.sendOtp(request.getEmail(), purpose);
            return ResponseEntity.ok(Map.of("status", "success", "message", "OTP sent to email"));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("status", "error", "message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    /**
     * Public check before signup or profile save (no auth).
     */
    @PostMapping("/contact/check-availability")
    public ResponseEntity<ContactAvailabilityDTO> checkContactAvailability(
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        try {
            String email = body != null ? body.get("email") : null;
            String phone = body != null ? body.get("phone") : null;
            String excludeUserId = null;
            try {
                excludeUserId = extractUserIdFromRequest(request);
            } catch (RuntimeException ignored) {
                // Public signup — never trust client-supplied excludeUserId
            }
            if (phone != null && !phone.isBlank()) {
                FreemiumService.validatePhone(phone);
            }
            return ResponseEntity.ok(
                    userContactService.checkAvailability(email, phone, excludeUserId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/otp/email/verify")
    public ResponseEntity<?> verifyEmailOtp(@RequestBody OtpVerifyRequestDTO request) {
        try {
            OtpVerifyResponseDTO response = otpService.verifyOtp(request.getEmail(), request.getOtp());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @GetMapping("/register/freemium/courses")
    public ResponseEntity<?> listFreemiumSignupCourses() {
        return ResponseEntity.ok(freemiumService.listSignupCourseOptions());
    }

    @PostMapping("/register/freemium")
    public ResponseEntity<?> registerFreemium(@RequestBody FreemiumRegisterRequestDTO request) {
        try {
            otpService.validateVerificationToken(request.getEmail(), request.getVerificationToken());
            User user = freemiumService.registerFreemiumUser(request);
            userService.recordLogin(user);
            UserDetails userDetails = new UserDetails(user.getEmail());
            int sessionVersion = userService.startNewSession(user.getId());
            String accessToken = jwtUtils.generateToken(userDetails, sessionVersion).get("accessToken");
            LoginResponseDTO response = UserMapper.toLoginResponse(user, accessToken, onboardingService);
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("status", "error", "message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/auth/google")
    public ResponseEntity<?> authGoogle(
            @RequestBody GoogleAuthRequestDTO request,
            @RequestParam(defaultValue = "false") boolean forceLogin) {
        return authWithOAuth(() -> oAuthAuthService.authenticateWithGoogle(request), forceLogin);
    }

    @PostMapping("/auth/apple")
    public ResponseEntity<?> authApple(
            @RequestBody AppleAuthRequestDTO request,
            @RequestParam(defaultValue = "false") boolean forceLogin) {
        return authWithOAuth(() -> oAuthAuthService.authenticateWithApple(request), forceLogin);
    }

    @PostMapping("/auth/email/continue")
    public ResponseEntity<?> emailContinue(
            @RequestBody EmailContinueRequestDTO request,
            @RequestParam(defaultValue = "false") boolean forceLogin) {
        return authWithOAuth(() -> oAuthAuthService.emailContinue(request), forceLogin);
    }

    @PostMapping("/auth/otp/continue")
    public ResponseEntity<?> otpContinue(
            @RequestBody OtpContinueRequestDTO request,
            @RequestParam(defaultValue = "false") boolean forceLogin) {
        return authWithOAuth(() -> oAuthAuthService.otpContinue(request), forceLogin);
    }

    @PostMapping("/me/onboarding/complete")
    public ResponseEntity<?> completeOnboarding(
            @RequestBody OnboardingCompleteRequestDTO request,
            HttpServletRequest httpRequest) {
        try {
            String userId = extractUserIdFromRequest(httpRequest);
            User user = oAuthAuthService.completeOnboarding(userId, request);
            userService.recordLogin(user);
            return ResponseEntity.ok(UserMapper.toSessionDto(user, onboardingService));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("status", "error", "message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        } catch (SkillamaAuthException e) {
            LOGGER.warn("Onboarding auth failed: {}", e.getMessage());
            return ResponseEntity.status(401).body(Map.of("status", "error", "message", e.getMessage()));
        } catch (RuntimeException e) {
            LOGGER.error("Onboarding complete failed: {}", e.getMessage());
            String msg = e.getMessage() != null ? e.getMessage() : "Failed to complete onboarding";
            if (msg.contains("non unique result")) {
                msg = "This mobile number is already linked to another account.";
            }
            return ResponseEntity.status(409).body(Map.of("status", "error", "message", msg));
        }
    }

    private ResponseEntity<?> authWithOAuth(java.util.function.Supplier<User> authenticator, boolean forceLogin) {
        try {
            User user = authenticator.get();
            if (!user.isActive()) {
                return ResponseEntity.status(403).body(Map.of(
                        "status", "error",
                        "message", "Account is not activated"));
            }
            if (hasConflictingSession(user, forceLogin)) {
                return sessionConflictResponse(user);
            }
            userService.recordLogin(user);
            int sessionVersion = userService.startNewSession(user.getId());
            String accessToken = jwtUtils.generateToken(new UserDetails(user.getEmail()), sessionVersion).get("accessToken");
            return ResponseEntity.ok(UserMapper.toLoginResponse(user, accessToken, onboardingService));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("status", "error", "message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/login/otp")
    public ResponseEntity<?> loginWithOtp(
            @RequestBody OtpLoginRequestDTO request,
            @RequestParam(defaultValue = "false") boolean forceLogin) {
        try {
            User user;
            if (request.getVerificationToken() != null && !request.getVerificationToken().isBlank()) {
                otpService.validateVerificationToken(request.getEmail(), request.getVerificationToken());
                user = userService.findByEmail(request.getEmail())
                        .orElseThrow(() -> new IllegalArgumentException("User not found. Register first."));
            } else if (request.getOtp() != null) {
                otpService.verifyOtp(request.getEmail(), request.getOtp());
                user = userService.findByEmail(request.getEmail())
                        .orElseThrow(() -> new IllegalArgumentException("User not found. Register first."));
            } else {
                return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "OTP or verificationToken required"));
            }
            if (!user.isActive()) {
                return ResponseEntity.status(403).body(Map.of("status", "error", "message", "Account is not activated"));
            }
            if (hasConflictingSession(user, forceLogin)) {
                return sessionConflictResponse(user);
            }
            userService.recordLogin(user);
            int sessionVersion = userService.startNewSession(user.getId());
            String accessToken = jwtUtils.generateToken(new UserDetails(user.getEmail()), sessionVersion).get("accessToken");
            return ResponseEntity.ok(UserMapper.toLoginResponse(user, accessToken, onboardingService));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    /**
     * Send OTP to migrate a legacy (no planTier) account to freemium.
     */
    @PostMapping("/migrate/freemium/otp/send")
    public ResponseEntity<?> sendMigrateFreemiumOtp(@RequestBody OtpSendRequestDTO request) {
        try {
            User user = userService.findByEmail(request.getEmail())
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            freemiumService.validateEligibleForFreemiumMigration(user);
            otpService.sendOtp(request.getEmail(), EmailOtp.OtpPurpose.MIGRATE_FREEMIUM);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "OTP sent to email for freemium migration"));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    /**
     * Confirm OTP and move account to freemium plan.
     */
    @PostMapping("/migrate/freemium/confirm")
    public ResponseEntity<?> confirmMigrateFreemium(@RequestBody MigrateFreemiumConfirmDTO request) {
        try {
            if (request.getVerificationToken() != null && !request.getVerificationToken().isBlank()) {
                otpService.validateVerificationToken(
                        request.getEmail(),
                        request.getVerificationToken(),
                        EmailOtp.OtpPurpose.MIGRATE_FREEMIUM);
            } else if (request.getOtp() != null) {
                otpService.verifyOtp(request.getEmail(), request.getOtp(), EmailOtp.OtpPurpose.MIGRATE_FREEMIUM);
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "error", "message", "otp or verificationToken required"));
            }
            FreemiumStatusDTO status = freemiumService.migrateLegacyUserToFreemium(
                    request.getEmail(), request.getPhone());
            return ResponseEntity.ok(Map.of("status", "success", "plan", status));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        } catch (com.prwatech.skillama.exception.ResourceNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> body) {
        try {
            String email = body != null ? body.get("email") : null;
            passwordResetService.sendForgotPasswordOtp(email);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "If this email is registered, a reset code has been sent"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequestDTO request) {
        try {
            passwordResetService.resetPassword(request);
            return ResponseEntity.ok(Map.of("status", "success", "message", "Password updated"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        } catch (com.prwatech.skillama.exception.ResourceNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    /**
     * Lightweight authenticated session snapshot (no token in body).
     */
    @GetMapping("/session")
    public ResponseEntity<?> getSession(HttpServletRequest request) {
        try {
            String userId = extractUserIdFromRequest(request);
            User user = userService.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));
            if (!user.isActive()) {
                return ResponseEntity.status(403).build();
            }
            return ResponseEntity.ok(UserMapper.toSessionDto(user, onboardingService));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(404).build();
        } catch (SkillamaAuthException e) {
            // "reason" lets the frontend's session poller tell "logged out elsewhere" apart
            // from a plain expired/invalid token, so it can show the right message.
            return ResponseEntity.status(401).body(Map.of("status", "error", "reason", e.getReason(), "message", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(401).build();
        }
    }

    /**
     * Revokes the calling token server-side (bumps tokenVersion) so it fails auth on any
     * future request, rather than remaining valid until its natural ~180-day expiry.
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        try {
            String userId = extractUserIdFromRequest(request);
            userService.logout(userId);
            return ResponseEntity.ok(Map.of("status", "success"));
        } catch (SkillamaAuthException e) {
            return ResponseEntity.status(401).build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(401).build();
        }
    }

    @GetMapping
    public ResponseEntity<?> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String order,
            HttpServletRequest request
    ) {
        try {
            String requesterId = extractUserIdFromRequest(request);
            User requester = userService.findById(requesterId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            if (requester.getRole() != User.UserRole.ADMIN && requester.getRole() != User.UserRole.OWNER) {
                return ResponseEntity.status(403).body(Map.of("message", "Admin access required"));
            }
            boolean desc = order.equalsIgnoreCase("desc");
            Page<User> users = userService.findAll(page, size, sortBy, desc);
            Page<UserPublicDTO> projected = users.map(UserMapper::toPublicDto);
            return ResponseEntity.ok(projected);
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("Authorization")) {
                return ResponseEntity.status(401).body(Map.of("message", "Unauthorized"));
            }
            return ResponseEntity.status(401).body(Map.of("message", "Unauthorized"));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserPublicDTO> getUserById(
            @PathVariable String id,
            HttpServletRequest request) {
        try {
            String requesterId = extractUserIdFromRequest(request);
            User requester = userService.findById(requesterId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            boolean isAdmin = requester.getRole() == User.UserRole.ADMIN
                    || requester.getRole() == User.UserRole.OWNER;
            if (!requesterId.equals(id) && !isAdmin) {
                return ResponseEntity.status(403).build();
            }
            return userService.findById(id)
                    .map(u -> ResponseEntity.ok(UserMapper.toPublicDto(u)))
                    .orElse(ResponseEntity.notFound().build());
        } catch (RuntimeException e) {
            return ResponseEntity.status(401).build();
        }
    }
    
    @PostMapping("/admin/activate")
    public ResponseEntity<?> activateUser(
            @RequestParam String email,
            HttpServletRequest request) {
        try {
            String actorId = extractUserIdFromRequest(request);
            adminService.activateUserByAdmin(email, actorId);
            return ResponseEntity.ok("User activated successfully");
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(404).body("User not found");
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("access required")) {
                return ResponseEntity.status(401).body(e.getMessage());
            }
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
    }
    
    @PostMapping("/admin/deactivate")
    public ResponseEntity<?> deactivateUser(
            @RequestParam String email,
            HttpServletRequest request) {
        try {
            String actorId = extractUserIdFromRequest(request);
            adminService.deactivateUserByAdmin(email, actorId);
            return ResponseEntity.ok("User deactivated successfully");
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(404).body("User not found");
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("access required")) {
                return ResponseEntity.status(401).body(e.getMessage());
            }
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
    }

    private String extractUserIdFromRequest(HttpServletRequest request) {
        return skillamaAuthSupport.resolveUserIdFromRequest(request);
    }
    
    @PostMapping("/admin/migrate-passwords")
    public ResponseEntity<?> migratePasswords() {
        return ResponseEntity.ok(userService.migrateAllPasswords());
    }
}

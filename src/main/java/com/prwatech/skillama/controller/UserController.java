package com.prwatech.skillama.controller;

import com.prwatech.authentication.security.JwtUtils;
import com.prwatech.common.dto.UserDetails;
import com.prwatech.skillama.dto.*;
import com.prwatech.skillama.model.EmailOtp;
import com.prwatech.skillama.model.User;
import com.prwatech.common.Constants;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.service.AdminService;
import com.prwatech.skillama.service.FreemiumService;
import com.prwatech.skillama.service.OtpService;
import com.prwatech.skillama.service.PasswordResetService;
import com.prwatech.skillama.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Optional;

@RestController("skillamaUserController")
@RequestMapping("/skillama/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final AdminService adminService;
    private final JwtUtils jwtUtils;
    private final OtpService otpService;
    private final FreemiumService freemiumService;
    private final PasswordResetService passwordResetService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user) {
        if (userService.findByEmail(user.getEmail()).isPresent()) {
            return ResponseEntity.status(409).body(Map.of(
                    "status", "error",
                    "message", "Email is already registered"));
        }
        return ResponseEntity.ok(userService.register(user));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody User loginRequest) {
        Optional<User> userOpt = userService.findByEmail(loginRequest.getEmail());
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (!user.isActive()) {
                return ResponseEntity.status(403).body("Account is not activated. Please contact admin.");
            }
            // Password comparison: passwords are stored encoded in DB
            if (userService.validatePassword(loginRequest.getPassword(), user.getPassword())) {
                userService.recordLogin(user);
                UserDetails userDetails = new UserDetails(user.getEmail());
                Map<String, String> tokens = jwtUtils.generateToken(userDetails);
                String accessToken = tokens.get("accessToken");
                
                LoginResponseDTO response = LoginResponseDTO.builder()
                    .id(user.getId())
                    .name(user.getName())
                    .email(user.getEmail())
                    .role(user.getRole() != null ? user.getRole() : User.UserRole.USER)
                    .active(user.isActive())
                    .gender(user.getGender())
                    .createdAt(user.getCreatedAt())
                    .token(accessToken)
                    .build();
                
                return ResponseEntity.ok(response);
            }
        }
        return ResponseEntity.status(401).body("Invalid credentials");
    }

    @PostMapping("/otp/email/send")
    public ResponseEntity<?> sendEmailOtp(@RequestBody OtpSendRequestDTO request) {
        try {
            EmailOtp.OtpPurpose purpose = request.getPurpose() != null ? request.getPurpose() : EmailOtp.OtpPurpose.SIGNUP;
            otpService.sendOtp(request.getEmail(), purpose);
            return ResponseEntity.ok(Map.of("status", "success", "message", "OTP sent to email"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
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
            String accessToken = jwtUtils.generateToken(userDetails).get("accessToken");
            LoginResponseDTO response = LoginResponseDTO.builder()
                    .id(user.getId())
                    .name(user.getName())
                    .email(user.getEmail())
                    .role(user.getRole())
                    .active(user.isActive())
                    .token(accessToken)
                    .build();
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("status", "error", "message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/login/otp")
    public ResponseEntity<?> loginWithOtp(@RequestBody OtpLoginRequestDTO request) {
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
            userService.recordLogin(user);
            String accessToken = jwtUtils.generateToken(new UserDetails(user.getEmail())).get("accessToken");
            return ResponseEntity.ok(LoginResponseDTO.builder()
                    .id(user.getId())
                    .name(user.getName())
                    .email(user.getEmail())
                    .role(user.getRole())
                    .active(user.isActive())
                    .token(accessToken)
                    .build());
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

    @GetMapping
    public ResponseEntity<Page<User>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String order
    ) {
        boolean desc = order.equalsIgnoreCase("desc");
        return ResponseEntity.ok(userService.findAll(page, size, sortBy, desc));
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable String id) {
        return userService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
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
        final String requestTokenHeader = request.getHeader(Constants.AUTH);
        if (requestTokenHeader == null || !requestTokenHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Authorization header missing or invalid");
        }
        String jwtToken = requestTokenHeader.substring(7);
        String email = jwtUtils.extractUsername(jwtToken);
        User user = userService.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return user.getId();
    }
    
    @PostMapping("/admin/migrate-passwords")
    public ResponseEntity<?> migratePasswords() {
        return ResponseEntity.ok(userService.migrateAllPasswords());
    }
}

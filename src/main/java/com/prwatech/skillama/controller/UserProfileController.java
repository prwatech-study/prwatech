package com.prwatech.skillama.controller;

import com.prwatech.authentication.security.JwtUtils;
import com.prwatech.skillama.dto.*;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.service.FreemiumService;
import com.prwatech.skillama.service.ReferralShareService;
import com.prwatech.skillama.service.LmsThemeService;
import com.prwatech.skillama.service.UpgradeRequestService;
import com.prwatech.skillama.service.ProgressReconciliationService;
import com.prwatech.skillama.service.UserProfileService;
import com.prwatech.skillama.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/skillama/user-profile")
@RequiredArgsConstructor
public class UserProfileController {
    
    private final UserProfileService userProfileService;
    private final UserService userService;
    private final FreemiumService freemiumService;
    private final ReferralShareService referralShareService;
    private final UpgradeRequestService upgradeRequestService;
    private final LmsThemeService lmsThemeService;
    private final ProgressReconciliationService progressReconciliationService;
    private final JwtUtils jwtUtils;
    
    private static final String SESSION_COOKIE_NAME = "skillama_session_id";
    
    // ========== Session Management ==========
    
    /**
     * Initialize guest session for non-logged-in users
     * Public endpoint - no authentication required
     */
    @PostMapping("/guest/init")
    public ResponseEntity<Map<String, Object>> initializeGuestSession(
            @RequestBody(required = false) InitGuestSessionRequestDTO request,
            HttpServletResponse response) {
        
        if (request == null) {
            request = InitGuestSessionRequestDTO.builder().build();
        }
        
        Map<String, Object> result = userProfileService.initializeGuestSession(request);
        String sessionId = (String) result.get("sessionId");
        
        // Set session cookie
        Cookie cookie = new Cookie(SESSION_COOKIE_NAME, sessionId);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(7 * 24 * 60 * 60); // 7 days
        response.addCookie(cookie);
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * Migrate guest session to user account
     * Requires authentication
     */
    @PostMapping("/guest/migrate")
    public ResponseEntity<Map<String, Object>> migrateGuestSession(
            @RequestBody MigrateGuestSessionRequestDTO request,
            HttpServletRequest httpRequest) {
        
        String userId = extractUserIdFromRequest(httpRequest);
        Map<String, Object> result = userProfileService.migrateGuestSession(userId, request.getSessionId());
        return ResponseEntity.ok(result);
    }
    
    // ========== Access Control ==========
    
    /**
     * Get complete access control information
     * Works for both logged-in and guest users
     * Automatically creates guest session if neither sessionId nor userId is present
     */
    @GetMapping("/access-control")
    public ResponseEntity<AccessControlResponseDTO> getAccessControl(
        @RequestParam(required = false) String courseId,
        @RequestParam(defaultValue = "false") boolean reconcile,
        HttpServletRequest request,
            HttpServletResponse response) {
        
        String sessionId = getSessionIdFromRequest(request);
        String userId = getUserIdFromRequest(request);
        
        // Log for debugging
        org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(UserProfileController.class);
        logger.debug("Access control request: userId={}, sessionId={}, courseId={}", userId, sessionId, courseId);
        
        // Auto guest session only when anonymous and no explicit course scope.
        // With courseId + Bearer token, learners must not hit guest-course bootstrap.
        if (sessionId == null && userId == null && !StringUtils.hasText(courseId)) {
            logger.debug("No session or user ID found, creating guest session");
            Map<String, Object> initResult = userProfileService.initializeGuestSession(
                    InitGuestSessionRequestDTO.builder().build());
            sessionId = (String) initResult.get("sessionId");
            
            // Set session cookie
            Cookie cookie = new Cookie(SESSION_COOKIE_NAME, sessionId);
            cookie.setHttpOnly(true);
            cookie.setPath("/");
            cookie.setMaxAge(7 * 24 * 60 * 60); // 7 days
            response.addCookie(cookie);
        }

        if (sessionId == null && userId == null) {
            return ResponseEntity.status(401).build();
        }

        // Optional: merge legacy progress (first LMS open only — not every course switch).
        if (reconcile && userId != null && StringUtils.hasText(courseId)) {
            try {
                progressReconciliationService.reconcileForUser(
                        userId,
                        ReconcileProgressRequestDTO.builder().courseId(courseId).build());
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(UserProfileController.class)
                        .warn("Access-control reconcile skipped for user {} course {}: {}",
                                userId, courseId, e.getMessage());
            }
        }
        
        AccessControlResponseDTO accessControl = userProfileService.getAccessControl(
                profilingSessionId(sessionId, userId), userId, courseId);
        return ResponseEntity.ok(accessControl);
    }
    
    /**
     * Check if specific lecture is accessible
     */
    @GetMapping("/lectures/{lectureLabel}/access")
    public ResponseEntity<LectureAccessDTO> checkLectureAccess(
            @PathVariable String lectureLabel,
            @RequestParam String courseId,
            HttpServletRequest request) {
        
        String sessionId = getSessionIdFromRequest(request);
        String userId = getUserIdFromRequest(request);
        
        LectureAccessDTO access = userProfileService.checkLectureAccess(
                profilingSessionId(sessionId, userId), userId, lectureLabel, courseId);
        return ResponseEntity.ok(access);
    }
    
    // ========== Lecture Tracking ==========
    
    /**
     * Mark lecture as completed
     */
    @PostMapping("/lectures/complete")
    public ResponseEntity<Map<String, Object>> completeLecture(
            @RequestBody CompleteLectureRequestDTO request,
            HttpServletRequest httpRequest) {
        
        String sessionId = getSessionIdFromRequest(httpRequest);
        String userId = getUserIdFromRequest(httpRequest);
        
        Map<String, Object> result = userProfileService.completeLecture(
                profilingSessionId(sessionId, userId), userId, request);
        return ResponseEntity.ok(result);
    }
    
    /**
     * Update lecture progress (in-progress)
     */
    @PostMapping("/lectures/progress")
    public ResponseEntity<Map<String, Object>> updateLectureProgress(
            @RequestBody UpdateLectureProgressRequestDTO request,
            HttpServletRequest httpRequest) {
        
        String sessionId = getSessionIdFromRequest(httpRequest);
        String userId = getUserIdFromRequest(httpRequest);
        
        Map<String, Object> result = userProfileService.updateLectureProgress(
                profilingSessionId(sessionId, userId), userId, request);
        return ResponseEntity.ok(result);
    }

    /**
     * Merge dashboard/legacy completion data into profiling (UserProfile) for LMS locks.
     */
    @PostMapping("/progress/reconcile")
    public ResponseEntity<ReconcileProgressResultDTO> reconcileProgress(
            @RequestBody ReconcileProgressRequestDTO request,
            HttpServletRequest httpRequest) {
        String userId = extractUserIdFromRequest(httpRequest);
        return ResponseEntity.ok(progressReconciliationService.reconcileForUser(userId, request));
    }
    
    // ========== Chat Tracking ==========

    /**
     * Paginated chat history (max 50 per page). Requires session cookie or Bearer token.
     */
    @GetMapping("/chat/history")
    public ResponseEntity<List<ChatHistoryItemDTO>> getChatHistory(
            @RequestParam(required = false) String courseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {
        String sessionId = getSessionIdFromRequest(httpRequest);
        String userId = getUserIdFromRequest(httpRequest);
        if (sessionId == null && userId == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(
                userProfileService.getChatHistory(
                        profilingSessionId(sessionId, userId), userId, courseId, page, size));
    }
    
    /**
     * Track chat question/answer
     */
    @PostMapping("/chat/track")
    public ResponseEntity<Map<String, Object>> trackChat(
            @RequestBody TrackChatRequestDTO request,
            HttpServletRequest httpRequest) {
        
        String sessionId = getSessionIdFromRequest(httpRequest);
        String userId = getUserIdFromRequest(httpRequest);
        
        Map<String, Object> result = userProfileService.trackChat(
                profilingSessionId(sessionId, userId), userId, request);
        return ResponseEntity.ok(result);
    }
    
    // ========== Freemium ==========

    @GetMapping("/freemium")
    public ResponseEntity<FreemiumStatusDTO> getFreemiumStatus(HttpServletRequest request) {
        String userId = extractUserIdFromRequest(request);
        return ResponseEntity.ok(freemiumService.getStatus(userId));
    }

    @PostMapping("/freemium/consume-query")
    public ResponseEntity<?> consumeQuery(
            @RequestBody(required = false) ConsumeQueryRequestDTO body,
            HttpServletRequest request) {
        try {
            String userId = extractUserIdFromRequest(request);
            return ResponseEntity.ok(freemiumService.consumeQuery(userId, body));
        } catch (com.prwatech.skillama.exception.QueryCreditLimitException e) {
            return ResponseEntity.status(429).body(Map.of(
                    "status", "error",
                    "message", e.getMessage(),
                    "queryCreditsUsed", e.getQueryCreditsUsed(),
                    "queryCreditsLimit", e.getQueryCreditsLimit()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(429).body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/upgrade-interest")
    public ResponseEntity<?> recordUpgradeInterest(
            @RequestBody UpgradeInterestRequestDTO body,
            HttpServletRequest request) {
        try {
            String userId = extractUserIdFromRequest(request);
            return ResponseEntity.ok(upgradeRequestService.recordInterest(userId, body));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("status", "error", "message", "Unauthorized"));
        }
    }

    @PostMapping("/lms-theme")
    public ResponseEntity<?> recordLmsThemeSwitch(
            @RequestBody LmsThemeSwitchRequestDTO body,
            HttpServletRequest request) {
        try {
            String userId = extractUserIdFromRequest(request);
            lmsThemeService.recordThemeSwitch(userId, body);
            return ResponseEntity.ok(Map.of("status", "success"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("status", "error", "message", "Unauthorized"));
        }
    }

    @PostMapping("/referral/apply")
    public ResponseEntity<?> applyReferral(
            @RequestBody ReferralApplyRequestDTO body,
            HttpServletRequest request) {
        try {
            String userId = extractUserIdFromRequest(request);
            return ResponseEntity.ok(freemiumService.applyReferral(userId, body.getCode()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @GetMapping("/referral/code")
    public ResponseEntity<Map<String, String>> getReferralCode(HttpServletRequest request) {
        String userId = extractUserIdFromRequest(request);
        return ResponseEntity.ok(Map.of("code", freemiumService.getReferralCode(userId)));
    }

    /** Referral code, signup link, and formatted share message for WhatsApp / email / social. */
    @GetMapping("/referral/share")
    public ResponseEntity<Map<String, Object>> getReferralSharePayload(HttpServletRequest request) {
        String userId = extractUserIdFromRequest(request);
        return ResponseEntity.ok(referralShareService.getSharePayload(userId));
    }

    @PostMapping("/referral/share/track")
    public ResponseEntity<Map<String, String>> trackReferralShare(
            @RequestBody ReferralShareTrackRequestDTO body,
            HttpServletRequest request) {
        try {
            String userId = extractUserIdFromRequest(request);
            referralShareService.trackShare(userId, body.getChannel());
            return ResponseEntity.ok(Map.of("status", "success"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    // ========== Helper Methods ==========
    
    private String getSessionIdFromRequest(HttpServletRequest request) {
        // Try to get from cookie first
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (SESSION_COOKIE_NAME.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        
        // Try to get from header
        String sessionHeader = request.getHeader("X-Session-Id");
        if (sessionHeader != null && !sessionHeader.isEmpty()) {
            return sessionHeader;
        }
        
        return null;
    }
    
    private String getUserIdFromRequest(HttpServletRequest request) {
        // Try to extract from Authorization header for logged-in users
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                String jwtToken = authHeader.substring(7);
                String email = jwtUtils.extractUsername(jwtToken);
                return userService.findByEmail(email)
                        .map(User::getId)
                        .orElse(null);
            } catch (Exception e) {
                // Invalid token or user not found
                return null;
            }
        }
        return null;
    }
    
    private String extractUserIdFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Authorization required");
        }
        
        String jwtToken = authHeader.substring(7);
        String email = jwtUtils.extractUsername(jwtToken);
        return userService.findByEmail(email)
                .map(User::getId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    /** Logged-in learners must use their user profile, not an old guest session cookie. */
    private static String profilingSessionId(String sessionId, String userId) {
        return userId != null ? null : sessionId;
    }
}


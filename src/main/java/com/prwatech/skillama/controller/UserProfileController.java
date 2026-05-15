package com.prwatech.skillama.controller;

import com.prwatech.authentication.security.JwtUtils;
import com.prwatech.skillama.dto.*;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.service.FreemiumService;
import com.prwatech.skillama.service.UserProfileService;
import com.prwatech.skillama.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

@RestController
@RequestMapping("/skillama/user-profile")
@RequiredArgsConstructor
public class UserProfileController {
    
    private final UserProfileService userProfileService;
    private final UserService userService;
    private final FreemiumService freemiumService;
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
            HttpServletRequest request,
            HttpServletResponse response) {
        
        String sessionId = getSessionIdFromRequest(request);
        String userId = getUserIdFromRequest(request);
        
        // Log for debugging
        org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(UserProfileController.class);
        logger.debug("Access control request: userId={}, sessionId={}, courseId={}", userId, sessionId, courseId);
        
        // If neither sessionId nor userId exists, create a new guest session
        if (sessionId == null && userId == null) {
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
        
        AccessControlResponseDTO accessControl = userProfileService.getAccessControl(sessionId, userId, courseId);
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
        
        LectureAccessDTO access = userProfileService.checkLectureAccess(sessionId, userId, lectureLabel, courseId);
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
        
        Map<String, Object> result = userProfileService.completeLecture(sessionId, userId, request);
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
        
        Map<String, Object> result = userProfileService.updateLectureProgress(sessionId, userId, request);
        return ResponseEntity.ok(result);
    }
    
    // ========== Chat Tracking ==========
    
    /**
     * Track chat question/answer
     */
    @PostMapping("/chat/track")
    public ResponseEntity<Map<String, Object>> trackChat(
            @RequestBody TrackChatRequestDTO request,
            HttpServletRequest httpRequest) {
        
        String sessionId = getSessionIdFromRequest(httpRequest);
        String userId = getUserIdFromRequest(httpRequest);
        
        Map<String, Object> result = userProfileService.trackChat(sessionId, userId, request);
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
        } catch (IllegalStateException e) {
            return ResponseEntity.status(429).body(Map.of("status", "error", "message", e.getMessage()));
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
}


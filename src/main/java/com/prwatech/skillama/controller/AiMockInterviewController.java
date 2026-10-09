package com.prwatech.skillama.controller;

import com.prwatech.skillama.service.AiMockInterviewService;
import com.prwatech.skillama.service.PlatformFeatureRolloutService;
import com.prwatech.skillama.service.SkillamaAuthSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/** Platform practice interviews. Feedback is visible to the candidate who started the session. */
@RestController
@RequestMapping("/skillama/ai-mock-interview")
@RequiredArgsConstructor
public class AiMockInterviewController {

    private final AiMockInterviewService aiMockInterviewService;
    private final SkillamaAuthSupport skillamaAuthSupport;
    private final PlatformFeatureRolloutService platformFeatureRolloutService;

    @GetMapping("/configs")
    public ResponseEntity<?> configs(HttpServletRequest request) {
        String userId = requireLearnerFeature(request);
        if (userId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(aiMockInterviewService.listConfigs());
    }

    @PostMapping("/start")
    public ResponseEntity<?> start(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String userId = requireLearnerFeature(request);
        if (userId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(aiMockInterviewService.start(userId, body));
    }

    @GetMapping("/my")
    public ResponseEntity<?> mine(HttpServletRequest request) {
        String userId = requireLearnerFeature(request);
        if (userId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(aiMockInterviewService.listMine(userId));
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<?> detail(@PathVariable String sessionId, HttpServletRequest request) {
        String userId = requireLearnerFeature(request);
        if (userId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(aiMockInterviewService.detail(userId, sessionId));
    }

    @PostMapping("/sessions/{sessionId}/join")
    public ResponseEntity<?> join(@PathVariable String sessionId, HttpServletRequest request) {
        String userId = requireLearnerFeature(request);
        if (userId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(aiMockInterviewService.join(userId, sessionId));
    }

    @PostMapping("/sessions/{sessionId}/turns")
    public ResponseEntity<?> turn(
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        String userId = requireLearnerFeature(request);
        if (userId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(aiMockInterviewService.addTurn(userId, sessionId, body));
    }

    @PostMapping("/sessions/{sessionId}/next")
    public ResponseEntity<?> next(@PathVariable String sessionId, HttpServletRequest request) {
        String userId = requireLearnerFeature(request);
        if (userId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(aiMockInterviewService.next(userId, sessionId));
    }

    @PostMapping("/sessions/{sessionId}/end")
    public ResponseEntity<?> end(@PathVariable String sessionId, HttpServletRequest request) {
        String userId = requireLearnerFeature(request);
        if (userId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(aiMockInterviewService.end(userId, sessionId));
    }

    @GetMapping("/admin/sessions")
    public ResponseEntity<?> adminSessions(
            @RequestParam(required = false) String status,
            HttpServletRequest request) {
        String userId = requireLearnerFeature(request);
        if (userId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(aiMockInterviewService.adminListSessions(userId, status));
    }

    @GetMapping("/admin/sessions/{sessionId}")
    public ResponseEntity<?> adminSessionDetail(
            @PathVariable String sessionId,
            HttpServletRequest request) {
        String userId = requireLearnerFeature(request);
        if (userId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(aiMockInterviewService.adminDetail(userId, sessionId));
    }

    private String requireLearnerFeature(HttpServletRequest request) {
        try {
            String userId = skillamaAuthSupport.resolveUserIdFromRequest(request);
            platformFeatureRolloutService.assertAccessible(
                    PlatformFeatureRolloutService.AI_MOCK_INTERVIEW, userId);
            return userId;
        } catch (RuntimeException ex) {
            if (ex instanceof com.prwatech.skillama.exception.FeatureNotLiveException) {
                throw ex;
            }
            return null;
        }
    }

    private ResponseEntity<Map<String, Object>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("status", 401, "error", "Unauthorized", "message", "Unauthorized"));
    }
}

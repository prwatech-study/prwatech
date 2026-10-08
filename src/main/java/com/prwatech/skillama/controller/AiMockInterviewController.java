package com.prwatech.skillama.controller;

import com.prwatech.skillama.service.AiMockInterviewService;
import com.prwatech.skillama.service.SkillamaAuthSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @GetMapping("/configs")
    public ResponseEntity<?> configs(HttpServletRequest request) {
        String userId = requireUser(request);
        if (userId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(aiMockInterviewService.listConfigs());
    }

    @PostMapping("/start")
    public ResponseEntity<?> start(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String userId = requireUser(request);
        if (userId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(aiMockInterviewService.start(userId, body));
    }

    @GetMapping("/my")
    public ResponseEntity<?> mine(HttpServletRequest request) {
        String userId = requireUser(request);
        if (userId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(aiMockInterviewService.listMine(userId));
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<?> detail(@PathVariable String sessionId, HttpServletRequest request) {
        String userId = requireUser(request);
        if (userId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(aiMockInterviewService.detail(userId, sessionId));
    }

    private String requireUser(HttpServletRequest request) {
        try {
            return skillamaAuthSupport.resolveUserIdFromRequest(request);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private ResponseEntity<Map<String, Object>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("status", 401, "error", "Unauthorized", "message", "Unauthorized"));
    }
}

package com.prwatech.skillama.controller;

import com.prwatech.skillama.dto.LectureRequestDTO;
import com.prwatech.skillama.exception.AiBudgetLimitException;
import com.prwatech.skillama.service.LectureService;
import com.prwatech.skillama.service.SkillamaAuthSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * Lecture-generation endpoint — logged-in learners only. The backend now mediates the
 * ai-tutor call (previously the browser hit ai-tutor directly), so the AI wallet budget
 * is enforced before the AI call runs, same as Debug/Code Execution/AI Exam already do.
 */
@RestController
@RequestMapping("/skillama/lecture")
@RequiredArgsConstructor
public class LectureController {

    private final LectureService lectureService;
    private final SkillamaAuthSupport skillamaAuthSupport;

    @PostMapping("/generate")
    public ResponseEntity<?> generate(@RequestBody LectureRequestDTO request, HttpServletRequest httpRequest) {
        String userId = resolveUserId(httpRequest);
        if (userId == null) {
            return unauthorized();
        }
        try {
            return ResponseEntity.ok(lectureService.generate(userId, request));
        } catch (AiBudgetLimitException e) {
            return ResponseEntity.status(429).body(Map.of(
                    "status", "error",
                    "message", e.getMessage(),
                    "aiBudgetLimitReached", true,
                    "aiCostUsedUsd", e.getAiCostUsedUsd(),
                    "aiCostLimitUsd", e.getAiCostLimitUsd()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(502).body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    private String resolveUserId(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        try {
            return skillamaAuthSupport.resolveUserIdFromRequest(request);
        } catch (Exception e) {
            return null;
        }
    }

    private ResponseEntity<Map<String, Object>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("status", "error", "message", "Unauthorized"));
    }
}

package com.prwatech.skillama.controller;

import com.prwatech.skillama.dto.StartExamRequestDTO;
import com.prwatech.skillama.dto.SubmitExamAttemptRequestDTO;
import com.prwatech.skillama.exception.AiBudgetLimitException;
import com.prwatech.skillama.service.ExamService;
import com.prwatech.skillama.service.SkillamaAuthSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/** AI Exam endpoints — logged-in learners only, same auth pattern as AI Mentor. */
@RestController
@RequestMapping("/skillama/ai-exam")
@RequiredArgsConstructor
public class ExamController {

    private final ExamService examService;
    private final SkillamaAuthSupport skillamaAuthSupport;

    @PostMapping("/start")
    public ResponseEntity<?> startExam(
            @RequestBody StartExamRequestDTO request, HttpServletRequest httpRequest) {
        String userId = resolveUserId(httpRequest);
        if (userId == null) {
            return unauthorized();
        }
        try {
            return ResponseEntity.ok(examService.startExam(userId, request));
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

    @PostMapping("/attempts")
    public ResponseEntity<?> submitAttempt(
            @RequestBody SubmitExamAttemptRequestDTO request, HttpServletRequest httpRequest) {
        String userId = resolveUserId(httpRequest);
        if (userId == null) {
            return unauthorized();
        }
        try {
            return ResponseEntity.ok(examService.submitAttempt(userId, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @GetMapping("/attempts")
    public ResponseEntity<?> listMyAttempts(
            @RequestParam(required = false) String courseId, HttpServletRequest httpRequest) {
        String userId = resolveUserId(httpRequest);
        if (userId == null) {
            return unauthorized();
        }
        List<?> attempts = examService.listMyAttempts(userId, courseId);
        return ResponseEntity.ok(attempts);
    }

    @GetMapping("/recommendation")
    public ResponseEntity<?> getRecommendation(
            @RequestParam String courseId, HttpServletRequest httpRequest) {
        String userId = resolveUserId(httpRequest);
        if (userId == null) {
            return unauthorized();
        }
        try {
            return ResponseEntity.ok(examService.getRecommendation(userId, courseId));
        } catch (AiBudgetLimitException e) {
            return ResponseEntity.status(429).body(Map.of(
                    "status", "error",
                    "message", e.getMessage(),
                    "aiBudgetLimitReached", true,
                    "aiCostUsedUsd", e.getAiCostUsedUsd(),
                    "aiCostLimitUsd", e.getAiCostLimitUsd()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
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

package com.prwatech.skillama.controller;

import com.prwatech.skillama.dto.GlobalAiExamCourseRequestDTO;
import com.prwatech.skillama.dto.StartExamRequestDTO;
import com.prwatech.skillama.dto.SubmitExamAttemptRequestDTO;
import com.prwatech.skillama.exception.AiBudgetLimitException;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.service.AdminPermissionService;
import com.prwatech.skillama.service.ExamService;
import com.prwatech.skillama.service.GlobalAiExamCourseService;
import com.prwatech.skillama.service.SkillamaAuthSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
    private final GlobalAiExamCourseService globalAiExamCourseService;
    private final AdminPermissionService adminPermissionService;

    @GetMapping("/global-courses")
    public ResponseEntity<?> listGlobalCourses(HttpServletRequest httpRequest) {
        String userId = resolveUserId(httpRequest);
        if (userId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(globalAiExamCourseService.listAll());
    }

    @PostMapping("/global-courses")
    public ResponseEntity<?> addGlobalCourse(
            @RequestBody GlobalAiExamCourseRequestDTO request, HttpServletRequest httpRequest) {
        String userId = resolveUserId(httpRequest);
        if (userId == null) {
            return unauthorized();
        }
        try {
            adminPermissionService.requireAdminOrOwner(userId);
            return ResponseEntity.ok(globalAiExamCourseService.create(request, userId));
        } catch (RuntimeException e) {
            return mapGlobalCourseWriteError(e);
        }
    }

    @PutMapping("/global-courses/{id}")
    public ResponseEntity<?> updateGlobalCourse(
            @PathVariable String id,
            @RequestBody GlobalAiExamCourseRequestDTO request,
            HttpServletRequest httpRequest) {
        String userId = resolveUserId(httpRequest);
        if (userId == null) {
            return unauthorized();
        }
        try {
            adminPermissionService.requireAdminOrOwner(userId);
            return ResponseEntity.ok(globalAiExamCourseService.update(id, request, userId));
        } catch (RuntimeException e) {
            return mapGlobalCourseWriteError(e);
        }
    }

    @DeleteMapping("/global-courses/{id}")
    public ResponseEntity<?> deleteGlobalCourse(
            @PathVariable String id, HttpServletRequest httpRequest) {
        String userId = resolveUserId(httpRequest);
        if (userId == null) {
            return unauthorized();
        }
        try {
            adminPermissionService.requireAdminOrOwner(userId);
            globalAiExamCourseService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return mapGlobalCourseWriteError(e);
        }
    }

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
            return ResponseEntity.status(429).body(e.toResponseBody());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(502).body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    /** Starts the exam clock and returns the questions — see ExamService.beginAttempt. */
    @PostMapping("/sessions/{examSessionId}/begin")
    public ResponseEntity<?> beginAttempt(
            @PathVariable String examSessionId, HttpServletRequest httpRequest) {
        String userId = resolveUserId(httpRequest);
        if (userId == null) {
            return unauthorized();
        }
        try {
            return ResponseEntity.ok(examService.beginAttempt(userId, examSessionId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
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

    @GetMapping("/progress")
    public ResponseEntity<?> getProgressOverview(
            @RequestParam String courseId, HttpServletRequest httpRequest) {
        String userId = resolveUserId(httpRequest);
        if (userId == null) {
            return unauthorized();
        }
        try {
            return ResponseEntity.ok(examService.getProgressOverview(userId, courseId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @GetMapping("/attempts/{attemptId}/dashboard")
    public ResponseEntity<?> getResultDashboard(
            @PathVariable String attemptId, HttpServletRequest httpRequest) {
        String userId = resolveUserId(httpRequest);
        if (userId == null) {
            return unauthorized();
        }
        try {
            return ResponseEntity.ok(examService.getResultDashboard(userId, attemptId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        }
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
            return ResponseEntity.status(429).body(e.toResponseBody());
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

    private ResponseEntity<Map<String, Object>> mapGlobalCourseWriteError(RuntimeException e) {
        String message = e.getMessage() != null ? e.getMessage() : "Request failed";
        if (e instanceof ResourceNotFoundException || message.toLowerCase().contains("not found")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("status", "error", "message", message));
        }
        if (e instanceof IllegalStateException || message.contains("already enabled")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("status", "error", "message", message));
        }
        if (message.contains("Admin access") || message.contains("Forbidden") || message.contains("Access denied")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("status", "error", "message", "Admin or Owner access required"));
        }
        if (e instanceof IllegalArgumentException) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", message));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("status", "error", "message", message));
    }
}

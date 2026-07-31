package com.prwatech.skillama.controller;

import com.prwatech.skillama.dto.EvaluationQuestionRequestDTO;
import com.prwatech.skillama.model.AdminModule;
import com.prwatech.skillama.model.AdminPermissionAction;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.service.AdminPermissionService;
import com.prwatech.skillama.service.EvaluationQuestionService;
import com.prwatech.skillama.service.SkillamaAuthSupport;
import com.prwatech.skillama.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequiredArgsConstructor
public class EvaluationQuestionController {

    private final EvaluationQuestionService evaluationQuestionService;
    private final AdminPermissionService adminPermissionService;
    private final SkillamaAuthSupport skillamaAuthSupport;
    private final UserService userService;

    /** Active question bank, grouped by category client-side — usable by Tester/Admin/Owner. */
    @GetMapping("/skillama/evaluation-questions")
    public ResponseEntity<?> listActiveQuestions(HttpServletRequest request) {
        try {
            String userId = skillamaAuthSupport.resolveUserIdFromRequest(request);
            User user = userService.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            if (user.getRole() == User.UserRole.USER) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            return ResponseEntity.ok(evaluationQuestionService.listActive());
        } catch (RuntimeException e) {
            return errorResponse(e);
        }
    }

    @GetMapping("/skillama/evaluation-questions/all")
    public ResponseEntity<?> listAllQuestions(HttpServletRequest request) {
        try {
            String userId = skillamaAuthSupport.resolveUserIdFromRequest(request);
            adminPermissionService.requirePermission(userId, AdminModule.TESTER_EVALUATIONS, AdminPermissionAction.READ);
            return ResponseEntity.ok(evaluationQuestionService.listAll());
        } catch (RuntimeException e) {
            return errorResponse(e);
        }
    }

    @PostMapping("/skillama/evaluation-questions")
    public ResponseEntity<?> create(@RequestBody EvaluationQuestionRequestDTO body, HttpServletRequest request) {
        try {
            String userId = skillamaAuthSupport.resolveUserIdFromRequest(request);
            adminPermissionService.requirePermission(userId, AdminModule.TESTER_EVALUATIONS, AdminPermissionAction.CREATE);
            return ResponseEntity.ok(evaluationQuestionService.create(body, userId));
        } catch (RuntimeException e) {
            return errorResponse(e);
        }
    }

    @PutMapping("/skillama/evaluation-questions/{id}")
    public ResponseEntity<?> update(
            @PathVariable String id,
            @RequestBody EvaluationQuestionRequestDTO body,
            HttpServletRequest request) {
        try {
            String userId = skillamaAuthSupport.resolveUserIdFromRequest(request);
            adminPermissionService.requirePermission(userId, AdminModule.TESTER_EVALUATIONS, AdminPermissionAction.UPDATE);
            return ResponseEntity.ok(evaluationQuestionService.update(id, body, userId));
        } catch (RuntimeException e) {
            return errorResponse(e);
        }
    }

    @DeleteMapping("/skillama/evaluation-questions/{id}")
    public ResponseEntity<?> delete(@PathVariable String id, HttpServletRequest request) {
        try {
            String userId = skillamaAuthSupport.resolveUserIdFromRequest(request);
            adminPermissionService.requirePermission(userId, AdminModule.TESTER_EVALUATIONS, AdminPermissionAction.DELETE);
            evaluationQuestionService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return errorResponse(e);
        }
    }

    private ResponseEntity<?> errorResponse(RuntimeException e) {
        String msg = e.getMessage() != null ? e.getMessage() : "";
        if (msg.contains("Insufficient permission") || msg.contains("Owner access")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (msg.contains("not found") || msg.contains("Not found")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if (msg.contains("Session expired") || msg.contains("Account not found") || msg.contains("Admin access")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(msg);
    }
}

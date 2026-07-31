package com.prwatech.skillama.controller;

import com.prwatech.skillama.dto.SubmitEvaluationRequestDTO;
import com.prwatech.skillama.model.AdminModule;
import com.prwatech.skillama.model.AdminPermissionAction;
import com.prwatech.skillama.model.TesterEvaluationResponse;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.service.AdminPermissionService;
import com.prwatech.skillama.service.SkillamaAuthSupport;
import com.prwatech.skillama.service.TesterEvaluationResponseService;
import com.prwatech.skillama.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;

@RestController
@RequiredArgsConstructor
public class TesterEvaluationResponseController {

    private final TesterEvaluationResponseService evaluationResponseService;
    private final AdminPermissionService adminPermissionService;
    private final SkillamaAuthSupport skillamaAuthSupport;
    private final UserService userService;

    @PostMapping("/skillama/evaluation-responses")
    public ResponseEntity<?> submit(@RequestBody SubmitEvaluationRequestDTO body, HttpServletRequest request) {
        try {
            String userId = skillamaAuthSupport.resolveUserIdFromRequest(request);
            User user = userService.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            if (user.getRole() == User.UserRole.USER) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            return ResponseEntity.ok(evaluationResponseService.submit(userId, body));
        } catch (RuntimeException e) {
            return errorResponse(e);
        }
    }

    @GetMapping("/skillama/evaluation-responses")
    public ResponseEntity<?> search(
            @RequestParam(required = false) String courseId,
            @RequestParam(required = false) String curriculumModuleId,
            @RequestParam(required = false) String testerId,
            @RequestParam(required = false) Boolean flaggedOnly,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        try {
            String userId = skillamaAuthSupport.resolveUserIdFromRequest(request);
            adminPermissionService.requirePermission(userId, AdminModule.TESTER_EVALUATIONS, AdminPermissionAction.READ);
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "submittedAt"));
            Page<TesterEvaluationResponse> result = evaluationResponseService.search(
                    courseId, curriculumModuleId, testerId, flaggedOnly, from, to, pageable);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return errorResponse(e);
        }
    }

    @GetMapping("/skillama/evaluation-responses/{id}")
    public ResponseEntity<?> getById(@PathVariable String id, HttpServletRequest request) {
        try {
            String userId = skillamaAuthSupport.resolveUserIdFromRequest(request);
            adminPermissionService.requirePermission(userId, AdminModule.TESTER_EVALUATIONS, AdminPermissionAction.READ);
            return ResponseEntity.ok(evaluationResponseService.getById(id));
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

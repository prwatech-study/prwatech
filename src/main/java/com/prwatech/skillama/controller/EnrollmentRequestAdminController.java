package com.prwatech.skillama.controller;

import com.prwatech.skillama.dto.ApiResponse;
import com.prwatech.skillama.dto.CourseEnrollmentRequestDTO;
import com.prwatech.skillama.dto.DenyCourseEnrollmentRequestDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.AdminModule;
import com.prwatech.skillama.model.AdminPermissionAction;
import com.prwatech.skillama.model.CourseEnrollmentRequest;
import com.prwatech.skillama.service.AdminAuditService;
import com.prwatech.skillama.service.AdminPermissionService;
import com.prwatech.skillama.service.CourseEnrollmentRequestService;
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
import java.util.List;
import java.util.Map;

/** Admin queue for learner enrollment requests: list, approve, deny. */
@RestController
@RequestMapping("/skillama/api/admin/enrollment-requests")
@RequiredArgsConstructor
public class EnrollmentRequestAdminController {

    private final CourseEnrollmentRequestService courseEnrollmentRequestService;
    private final AdminPermissionService adminPermissionService;
    private final SkillamaAuthSupport skillamaAuthSupport;
    private final AdminAuditService adminAuditService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CourseEnrollmentRequestDTO>>> listRequests(
            @RequestParam(required = false) CourseEnrollmentRequest.RequestStatus status,
            HttpServletRequest httpRequest) {
        try {
            adminPermissionService.requirePermission(
                    skillamaAuthSupport.resolveUserIdFromRequest(httpRequest),
                    AdminModule.ENROLLMENT_REQUESTS, AdminPermissionAction.READ);
            return ResponseEntity.ok(new ApiResponse<>(200,
                    courseEnrollmentRequestService.listRequests(status)));
        } catch (RuntimeException e) {
            return forbiddenOrUnauthorized(e);
        }
    }

    @PostMapping("/{requestId}/approve")
    public ResponseEntity<ApiResponse<CourseEnrollmentRequestDTO>> approve(
            @PathVariable String requestId, HttpServletRequest httpRequest) {
        try {
            String adminId = skillamaAuthSupport.resolveUserIdFromRequest(httpRequest);
            adminPermissionService.requirePermission(
                    adminId, AdminModule.ENROLLMENT_REQUESTS, AdminPermissionAction.UPDATE);
            CourseEnrollmentRequestDTO result = courseEnrollmentRequestService.approve(requestId, adminId);
            adminAuditService.log(adminId, "ENROLLMENT_REQUEST_APPROVE", "USER", result.getUserId(),
                    "Approved enrollment request for course " + result.getCourseName(), null);
            return ResponseEntity.ok(new ApiResponse<>(200, result));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(404, null));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, null));
        } catch (RuntimeException e) {
            return forbiddenOrUnauthorized(e);
        }
    }

    @PostMapping("/{requestId}/deny")
    public ResponseEntity<ApiResponse<CourseEnrollmentRequestDTO>> deny(
            @PathVariable String requestId,
            @RequestBody DenyCourseEnrollmentRequestDTO body,
            HttpServletRequest httpRequest) {
        try {
            String adminId = skillamaAuthSupport.resolveUserIdFromRequest(httpRequest);
            adminPermissionService.requirePermission(
                    adminId, AdminModule.ENROLLMENT_REQUESTS, AdminPermissionAction.UPDATE);
            CourseEnrollmentRequestDTO result = courseEnrollmentRequestService.deny(
                    requestId, adminId, body != null ? body.getReason() : null);
            adminAuditService.log(adminId, "ENROLLMENT_REQUEST_DENY", "USER", result.getUserId(),
                    "Denied enrollment request for course " + result.getCourseName()
                            + " reason=" + result.getDecisionReason(), null);
            return ResponseEntity.ok(new ApiResponse<>(200, result));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(404, null));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, null));
        } catch (RuntimeException e) {
            return forbiddenOrUnauthorized(e);
        }
    }

    private <T> ResponseEntity<ApiResponse<T>> forbiddenOrUnauthorized(RuntimeException e) {
        String message = e.getMessage() != null ? e.getMessage() : "";
        if (message.contains("Insufficient permission") || message.contains("Owner access required")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, null));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
    }
}

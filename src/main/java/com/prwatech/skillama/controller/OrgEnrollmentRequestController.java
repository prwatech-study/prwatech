package com.prwatech.skillama.controller;

import com.prwatech.skillama.dto.ApiResponse;
import com.prwatech.skillama.dto.CourseEnrollmentRequestDTO;
import com.prwatech.skillama.dto.DenyCourseEnrollmentRequestDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.CourseEnrollmentRequest;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.service.OrgEnrollmentRequestService;
import com.prwatech.skillama.service.SkillamaAuthSupport;
import com.prwatech.skillama.service.TenantSecurityService;
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

/** Organization owner/admin queue for learner enrollment requests. */
@RestController
@RequestMapping("/skillama/api/org/enrollment-requests")
@RequiredArgsConstructor
public class OrgEnrollmentRequestController {

    private final OrgEnrollmentRequestService orgEnrollmentRequestService;
    private final SkillamaAuthSupport skillamaAuthSupport;
    private final TenantSecurityService tenantSecurityService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CourseEnrollmentRequestDTO>>> listRequests(
            @RequestParam(required = false) CourseEnrollmentRequest.RequestStatus status,
            HttpServletRequest request) {
        try {
            User actor = tenantSecurityService.requireUser(skillamaAuthSupport.resolveUserIdFromRequest(request));
            return ResponseEntity.ok(new ApiResponse<>(200, orgEnrollmentRequestService.list(actor, status)));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, null));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    @PostMapping("/{requestId}/approve")
    public ResponseEntity<ApiResponse<CourseEnrollmentRequestDTO>> approve(
            @PathVariable String requestId, HttpServletRequest request) {
        try {
            User actor = tenantSecurityService.requireUser(skillamaAuthSupport.resolveUserIdFromRequest(request));
            return ResponseEntity.ok(new ApiResponse<>(200, orgEnrollmentRequestService.approve(actor, requestId)));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(404, null));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, null));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    @PostMapping("/{requestId}/deny")
    public ResponseEntity<ApiResponse<CourseEnrollmentRequestDTO>> deny(
            @PathVariable String requestId,
            @RequestBody DenyCourseEnrollmentRequestDTO body,
            HttpServletRequest request) {
        try {
            User actor = tenantSecurityService.requireUser(skillamaAuthSupport.resolveUserIdFromRequest(request));
            return ResponseEntity.ok(new ApiResponse<>(200,
                    orgEnrollmentRequestService.deny(actor, requestId, body != null ? body.getReason() : null)));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(404, null));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, null));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }
}

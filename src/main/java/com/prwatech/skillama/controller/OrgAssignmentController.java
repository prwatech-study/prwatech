package com.prwatech.skillama.controller;

import com.prwatech.skillama.dto.*;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.service.OrgAssignmentService;
import com.prwatech.skillama.service.SkillamaAuthSupport;
import com.prwatech.skillama.service.TenantSecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/skillama/api/org/assignments")
@RequiredArgsConstructor
public class OrgAssignmentController {

    private final OrgAssignmentService orgAssignmentService;
    private final SkillamaAuthSupport skillamaAuthSupport;
    private final TenantSecurityService tenantSecurityService;

    @GetMapping("/courses")
    public ResponseEntity<ApiResponse<List<OrgCourseOptionDTO>>> listCourses(HttpServletRequest request) {
        try {
            User actor = tenantSecurityService.requireUser(skillamaAuthSupport.resolveUserIdFromRequest(request));
            return ResponseEntity.ok(new ApiResponse<>(200, orgAssignmentService.listAssignableCourses(actor)));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, null));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    @PostMapping("/users/{userId}/courses")
    public ResponseEntity<ApiResponse<AssignmentResponseDTO>> assignCourses(
            @PathVariable String userId,
            @RequestBody OrgAssignCoursesRequestDTO body,
            HttpServletRequest request) {
        try {
            User actor = tenantSecurityService.requireUser(skillamaAuthSupport.resolveUserIdFromRequest(request));
            return ResponseEntity.ok(new ApiResponse<>(200,
                    orgAssignmentService.assignCourses(actor, userId, body)));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, null));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    @DeleteMapping("/users/{userId}/courses/{courseId}")
    public ResponseEntity<ApiResponse<Void>> unassignCourse(
            @PathVariable String userId,
            @PathVariable String courseId,
            HttpServletRequest request) {
        try {
            User actor = tenantSecurityService.requireUser(skillamaAuthSupport.resolveUserIdFromRequest(request));
            orgAssignmentService.unassignCourse(actor, userId, courseId);
            return ResponseEntity.ok(new ApiResponse<>(200, null));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, null));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }
}

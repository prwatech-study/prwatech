package com.prwatech.skillama.controller;

import com.prwatech.skillama.dto.ApiResponse;
import com.prwatech.skillama.dto.CourseCatalogItemDTO;
import com.prwatech.skillama.dto.CourseEnrollmentRequestDTO;
import com.prwatech.skillama.dto.CreateCourseEnrollmentRequestDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.CourseEnrollmentRequest;
import com.prwatech.skillama.service.CourseEnrollmentRequestService;
import com.prwatech.skillama.service.SkillamaAuthSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * Learner-facing Explore catalog: browse assignable courses and request
 * enrollment (admin approves/denies — no direct self-enroll).
 */
@RestController
@RequestMapping("/skillama/api/users/me")
@RequiredArgsConstructor
public class CourseCatalogController {

    private final CourseEnrollmentRequestService courseEnrollmentRequestService;
    private final SkillamaAuthSupport skillamaAuthSupport;

    @GetMapping("/course-catalog")
    public ResponseEntity<?> getCourseCatalog(HttpServletRequest httpRequest) {
        String userId = resolveUserId(httpRequest);
        if (userId == null) {
            return unauthorized();
        }
        List<CourseCatalogItemDTO> catalog = courseEnrollmentRequestService.listCatalog(userId);
        return ResponseEntity.ok(new ApiResponse<>(200, catalog));
    }

    @PostMapping("/course-requests")
    public ResponseEntity<?> createRequest(
            @RequestBody CreateCourseEnrollmentRequestDTO body, HttpServletRequest httpRequest) {
        String userId = resolveUserId(httpRequest);
        if (userId == null) {
            return unauthorized();
        }
        try {
            CourseEnrollmentRequest request = courseEnrollmentRequestService.createRequest(userId, body);
            return ResponseEntity.ok(new ApiResponse<>(200, Map.of(
                    "id", request.getId(),
                    "courseId", request.getCourseId(),
                    "status", request.getStatus().name())));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @GetMapping("/course-requests")
    public ResponseEntity<?> listMyRequests(HttpServletRequest httpRequest) {
        String userId = resolveUserId(httpRequest);
        if (userId == null) {
            return unauthorized();
        }
        List<CourseEnrollmentRequestDTO> requests = courseEnrollmentRequestService.listMyRequests(userId);
        return ResponseEntity.ok(new ApiResponse<>(200, requests));
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

package com.prwatech.skillama.controller;

import com.prwatech.authentication.security.JwtUtils;
import com.prwatech.skillama.dto.ApiResponse;
import com.prwatech.skillama.dto.CourseProgressDTO;
import com.prwatech.skillama.dto.EnrollUserRequest;
import com.prwatech.skillama.dto.UpdateProgressRequest;
import com.prwatech.skillama.dto.UserCourseDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.model.UserCourseEnrollment;
import com.prwatech.skillama.service.UserCourseService;
import com.prwatech.skillama.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserCourseController {
    
    private final UserCourseService userCourseService;
    private final UserService userService;
    private final JwtUtils jwtUtils;
    
    /**
     * Get all courses assigned/purchased by the authenticated user with progress
     */
    @GetMapping("/courses")
    public ResponseEntity<ApiResponse<List<UserCourseDTO>>> getUserCourses(
            HttpServletRequest request) {
        try {
            String userId = extractUserIdFromRequest(request);
            List<UserCourseDTO> courses = userCourseService.getUserCoursesWithProgress(userId);
            return ResponseEntity.ok(new ApiResponse<>(200, courses));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, null));
        }
    }
    
    /**
     * Get detailed progress for a specific course
     */
    @GetMapping("/courses/{courseId}/progress")
    public ResponseEntity<ApiResponse<CourseProgressDTO>> getCourseProgress(
            @PathVariable String courseId,
            HttpServletRequest request) {
        try {
            String userId = extractUserIdFromRequest(request);
            CourseProgressDTO progress = userCourseService.getCourseProgress(userId, courseId);
            return ResponseEntity.ok(new ApiResponse<>(200, progress));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, null));
        }
    }
    
    /**
     * Update course progress when a lecture is completed
     */
    @PutMapping("/courses/{courseId}/progress")
    public ResponseEntity<ApiResponse<CourseProgressDTO>> updateCourseProgress(
            @PathVariable String courseId,
            @RequestBody UpdateProgressRequest requestBody,
            HttpServletRequest request) {
        try {
            String userId = extractUserIdFromRequest(request);
            CourseProgressDTO progress = userCourseService.updateProgress(
                userId, courseId, requestBody.getLectureId(), 
                requestBody.isCompleted(), requestBody.getTimeSpent());
            return ResponseEntity.ok(new ApiResponse<>(200, progress));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, null));
        }
    }
    
    /**
     * Enroll user to a course
     */
    @PostMapping("/courses/enroll")
    public ResponseEntity<ApiResponse<UserCourseEnrollment>> enrollToCourse(
            @RequestBody EnrollUserRequest enrollRequest,
            HttpServletRequest request) {
        try {
            String userId = extractUserIdFromRequest(request);
            UserCourseEnrollment enrollment = userCourseService.enrollUserToCourse(
                userId, 
                enrollRequest.getCourseId(), 
                enrollRequest.getEnrollmentType() != null 
                    ? enrollRequest.getEnrollmentType() 
                    : UserCourseEnrollment.EnrollmentType.ASSIGNED
            );
            return ResponseEntity.ok(new ApiResponse<>(200, enrollment));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, null));
        }
    }
    
    /**
     * Unenroll user from a course
     */
    @DeleteMapping("/courses/{courseId}/enroll")
    public ResponseEntity<ApiResponse<String>> unenrollFromCourse(
            @PathVariable String courseId,
            HttpServletRequest request) {
        try {
            String userId = extractUserIdFromRequest(request);
            userCourseService.unenrollUserFromCourse(userId, courseId);
            return ResponseEntity.ok(new ApiResponse<>(200, "Successfully unenrolled from course"));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, null));
        }
    }
    
    /**
     * Update user profile
     */
    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<UserDTO>> updateProfile(
            @RequestBody com.prwatech.skillama.dto.UpdateProfileRequest requestBody,
            HttpServletRequest request) {
        try {
            String userId = extractUserIdFromRequest(request);
            User user = userService.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
            
            // Update allowed fields
            if (requestBody.getName() != null) {
                user.setName(requestBody.getName());
            }
            if (requestBody.getEmail() != null) {
                // Check if email is already taken by another user
                userService.findByEmail(requestBody.getEmail())
                    .ifPresent(existing -> {
                        if (!existing.getId().equals(userId)) {
                            throw new IllegalArgumentException("Email already exists");
                        }
                    });
                user.setEmail(requestBody.getEmail());
            }
            if (requestBody.getGender() != null) {
                user.setGender(requestBody.getGender());
            }
            
            user.setUpdatedAt(java.time.LocalDateTime.now());
            user = userService.save(user);
            
            // Convert to DTO
            UserDTO dto = new UserDTO();
            dto.setId(user.getId());
            dto.setName(user.getName());
            dto.setEmail(user.getEmail());
            dto.setRole(user.getRole() != null ? user.getRole() : User.UserRole.USER);
            dto.setActive(user.isActive());
            dto.setGender(user.getGender() != null ? user.getGender().toString() : null);
            dto.setCreatedAt(user.getCreatedAt());
            dto.setUpdatedAt(user.getUpdatedAt());
            
            return ResponseEntity.ok(new ApiResponse<>(200, dto));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, null));
        }
    }
    
    /**
     * Extracts userId from JWT token in Authorization header
     * The JWT subject contains the user's email, which is used to find the user and get their ID
     */
    private String extractUserIdFromRequest(HttpServletRequest request) {
        final String requestTokenHeader = request.getHeader("Authorization");
        
        if (requestTokenHeader == null || !requestTokenHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Authorization header missing or invalid");
        }
        
        String jwtToken = requestTokenHeader.substring(7);
        String email = jwtUtils.extractUsername(jwtToken);
        
        // Find user by email to get the MongoDB id
        User user = userService.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        return user.getId();
    }
}


package com.prwatech.skillama.controller;

import com.prwatech.authentication.security.JwtUtils;
import com.prwatech.common.Constants;
import com.prwatech.skillama.dto.ApiResponse;
import com.prwatech.skillama.dto.CourseProgressDTO;
import com.prwatech.skillama.dto.EnrollUserRequest;
import com.prwatech.skillama.dto.UpdateProgressRequest;
import com.prwatech.skillama.dto.UserCourseDTO;
import com.prwatech.skillama.dto.UserDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.model.UserCourseEnrollment;
import com.prwatech.skillama.service.FreemiumService;
import com.prwatech.skillama.service.UserContactService;
import com.prwatech.skillama.service.UserCourseService;
import com.prwatech.skillama.service.UserService;
import com.prwatech.skillama.util.IndiaTime;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/skillama/api/users/me")
@RequiredArgsConstructor
public class SkillamaUserCourseController {
    
    private final UserCourseService userCourseService;
    private final UserService userService;
    private final FreemiumService freemiumService;
    private final UserContactService userContactService;
    private final JwtUtils jwtUtils;
    
    /**
     * Get all courses assigned/purchased by the authenticated user with progress
     */
    @ApiOperation(value = "Get user courses with progress", notes = "Get all courses assigned/purchased by the authenticated user with progress information")
    @ApiResponses(value = {
            @io.swagger.annotations.ApiResponse(code = 200, message = "Success"),
            @io.swagger.annotations.ApiResponse(code = 401, message = "Unauthorized"),
            @io.swagger.annotations.ApiResponse(code = 500, message = "Internal server error")
    })
    @ApiImplicitParams({
            @ApiImplicitParam(
                    name = Constants.AUTH,
                    value = Constants.TOKEN_TYPE,
                    required = true,
                    dataType = Constants.AUTH_DATA_TYPE,
                    paramType = Constants.AUTH_PARAM_TYPE)
    })
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
    @ApiOperation(value = "Get course progress", notes = "Get detailed progress for a specific course for the authenticated user")
    @ApiResponses(value = {
            @io.swagger.annotations.ApiResponse(code = 200, message = "Success"),
            @io.swagger.annotations.ApiResponse(code = 401, message = "Unauthorized"),
            @io.swagger.annotations.ApiResponse(code = 404, message = "Course not found"),
            @io.swagger.annotations.ApiResponse(code = 500, message = "Internal server error")
    })
    @ApiImplicitParams({
            @ApiImplicitParam(
                    name = Constants.AUTH,
                    value = Constants.TOKEN_TYPE,
                    required = true,
                    dataType = Constants.AUTH_DATA_TYPE,
                    paramType = Constants.AUTH_PARAM_TYPE)
    })
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
    @ApiOperation(value = "Update course progress", notes = "Update course progress when a lecture is completed")
    @ApiResponses(value = {
            @io.swagger.annotations.ApiResponse(code = 200, message = "Success"),
            @io.swagger.annotations.ApiResponse(code = 401, message = "Unauthorized"),
            @io.swagger.annotations.ApiResponse(code = 404, message = "Course or lecture not found"),
            @io.swagger.annotations.ApiResponse(code = 500, message = "Internal server error")
    })
    @ApiImplicitParams({
            @ApiImplicitParam(
                    name = Constants.AUTH,
                    value = Constants.TOKEN_TYPE,
                    required = true,
                    dataType = Constants.AUTH_DATA_TYPE,
                    paramType = Constants.AUTH_PARAM_TYPE)
    })
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
    @ApiOperation(value = "Enroll to course", notes = "Enroll the authenticated user to a course")
    @ApiResponses(value = {
            @io.swagger.annotations.ApiResponse(code = 200, message = "Success"),
            @io.swagger.annotations.ApiResponse(code = 401, message = "Unauthorized"),
            @io.swagger.annotations.ApiResponse(code = 404, message = "Course not found"),
            @io.swagger.annotations.ApiResponse(code = 500, message = "Internal server error")
    })
    @ApiImplicitParams({
            @ApiImplicitParam(
                    name = Constants.AUTH,
                    value = Constants.TOKEN_TYPE,
                    required = true,
                    dataType = Constants.AUTH_DATA_TYPE,
                    paramType = Constants.AUTH_PARAM_TYPE)
    })
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
    @ApiOperation(value = "Unenroll from course", notes = "Unenroll the authenticated user from a course")
    @ApiResponses(value = {
            @io.swagger.annotations.ApiResponse(code = 200, message = "Success"),
            @io.swagger.annotations.ApiResponse(code = 401, message = "Unauthorized"),
            @io.swagger.annotations.ApiResponse(code = 404, message = "Course or enrollment not found"),
            @io.swagger.annotations.ApiResponse(code = 500, message = "Internal server error")
    })
    @ApiImplicitParams({
            @ApiImplicitParam(
                    name = Constants.AUTH,
                    value = Constants.TOKEN_TYPE,
                    required = true,
                    dataType = Constants.AUTH_DATA_TYPE,
                    paramType = Constants.AUTH_PARAM_TYPE)
    })
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
     * Get authenticated user's profile (includes phone and plan tier).
     */
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserDTO>> getProfile(HttpServletRequest request) {
        try {
            String userId = extractUserIdFromRequest(request);
            User user = userService.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
            return ResponseEntity.ok(new ApiResponse<>(200, toUserDto(user)));
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
    @ApiOperation(value = "Update user profile", notes = "Update the authenticated user's profile information")
    @ApiResponses(value = {
            @io.swagger.annotations.ApiResponse(code = 200, message = "Success"),
            @io.swagger.annotations.ApiResponse(code = 400, message = "Bad request (e.g., email already exists)"),
            @io.swagger.annotations.ApiResponse(code = 401, message = "Unauthorized"),
            @io.swagger.annotations.ApiResponse(code = 404, message = "User not found"),
            @io.swagger.annotations.ApiResponse(code = 500, message = "Internal server error")
    })
    @ApiImplicitParams({
            @ApiImplicitParam(
                    name = Constants.AUTH,
                    value = Constants.TOKEN_TYPE,
                    required = true,
                    dataType = Constants.AUTH_DATA_TYPE,
                    paramType = Constants.AUTH_PARAM_TYPE)
    })
    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(
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
                user.setEmail(userContactService.normalizeEmail(requestBody.getEmail()));
            }
            if (requestBody.getGender() != null) {
                user.setGender(requestBody.getGender());
            }
            if (requestBody.getPhone() != null && !requestBody.getPhone().isBlank()) {
                FreemiumService.validatePhone(requestBody.getPhone());
                String newPhone = FreemiumService.normalizePhone(requestBody.getPhone());
                userContactService.assertContactUnique(user.getEmail(), newPhone, userId);
                user.setPhone(newPhone);
            }
            
            user.setUpdatedAt(IndiaTime.now());
            user = userService.save(user);
            
            return ResponseEntity.ok(new ApiResponse<>(200, toUserDto(user)));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, null));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("status", 409, "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, null));
        }
    }
    
    private static UserDTO toUserDto(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setName(user.getName());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        dto.setPlanTier(user.getPlanTier());
        dto.setRole(user.getRole() != null ? user.getRole() : User.UserRole.USER);
        dto.setActive(user.isActive());
        dto.setGender(user.getGender() != null ? user.getGender().toString() : null);
        dto.setCreatedAt(user.getCreatedAt());
        dto.setUpdatedAt(user.getUpdatedAt());
        dto.setLastLoginAt(user.getLastLoginAt());
        dto.setLoginCount(user.getLoginCount());
        return dto;
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


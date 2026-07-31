package com.prwatech.skillama.controller;

import com.prwatech.common.Constants;
import com.prwatech.skillama.dto.ApiResponse;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.model.UserCourseEnrollment;
import com.prwatech.skillama.repository.CourseRepository;
import com.prwatech.skillama.repository.UserCourseEnrollmentRepository;
import com.prwatech.skillama.service.SkillamaAuthSupport;
import com.prwatech.skillama.service.UserCourseService;
import com.prwatech.skillama.service.UserService;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin controller for course enrollment management.
 * Restricted to ADMIN, OWNER, and TESTER — these endpoints can bulk-enroll every
 * user into every course, so they must never be reachable without a role check.
 */
@RestController
@RequestMapping("/skillama/api/admin/courses")
@RequiredArgsConstructor
public class AdminCourseEnrollmentController {

    private final UserCourseService userCourseService;
    private final UserService userService;
    private final CourseRepository courseRepository;
    private final UserCourseEnrollmentRepository enrollmentRepository;
    private final SkillamaAuthSupport skillamaAuthSupport;
    
    /**
     * Enroll a specific user to a specific course (Admin)
     */
    @ApiOperation(value = "Enroll user to course", notes = "Enroll a specific user to a specific course (Admin only)")
    @ApiResponses(value = {
            @io.swagger.annotations.ApiResponse(code = 200, message = "Success"),
            @io.swagger.annotations.ApiResponse(code = 400, message = "Bad request"),
            @io.swagger.annotations.ApiResponse(code = 401, message = "Unauthorized"),
            @io.swagger.annotations.ApiResponse(code = 403, message = "Forbidden - Admin access required"),
            @io.swagger.annotations.ApiResponse(code = 404, message = "User or course not found"),
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
    @PostMapping("/{courseId}/enroll/{userId}")
    public ResponseEntity<ApiResponse<UserCourseEnrollment>> enrollUserToCourse(
            @PathVariable String courseId,
            @PathVariable String userId,
            @RequestParam(required = false) UserCourseEnrollment.EnrollmentType enrollmentType,
            HttpServletRequest request) {
        try {
            verifyAccess(request);
        } catch (RuntimeException e) {
            return accessDeniedResponse(e);
        }
        try {
            UserCourseEnrollment enrollment = userCourseService.enrollUserToCourse(
                userId, 
                courseId, 
                enrollmentType != null ? enrollmentType : UserCourseEnrollment.EnrollmentType.ASSIGNED
            );
            return ResponseEntity.ok(new ApiResponse<>(200, enrollment));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, null));
        }
    }
    
    /**
     * Enroll all users to a specific course (Admin - Migration utility)
     */
    @ApiOperation(value = "Enroll all users to course", notes = "Enroll all users to a specific course - Migration utility (Admin only)")
    @ApiResponses(value = {
            @io.swagger.annotations.ApiResponse(code = 200, message = "Success"),
            @io.swagger.annotations.ApiResponse(code = 400, message = "Bad request"),
            @io.swagger.annotations.ApiResponse(code = 401, message = "Unauthorized"),
            @io.swagger.annotations.ApiResponse(code = 403, message = "Forbidden - Admin access required"),
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
    @PostMapping("/{courseId}/enroll-all")
    public ResponseEntity<ApiResponse<Map<String, Object>>> enrollAllUsersToCourse(
            @PathVariable String courseId,
            @RequestParam(required = false) UserCourseEnrollment.EnrollmentType enrollmentType,
            HttpServletRequest request) {
        try {
            verifyAccess(request);
        } catch (RuntimeException e) {
            return accessDeniedResponse(e);
        }
        try {
            // Verify course exists
            Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));
            
            // Get all users (using a large page size to get all)
            List<User> allUsers = userService.findAll(0, 10000, "createdAt", false).getContent();
            
            int enrolled = 0;
            int alreadyEnrolled = 0;
            int errors = 0;
            
            for (User user : allUsers) {
                try {
                    if (userCourseService.isUserEnrolled(user.getId(), courseId)) {
                        alreadyEnrolled++;
                    } else {
                        userCourseService.enrollUserToCourse(
                            user.getId(), 
                            courseId, 
                            enrollmentType != null ? enrollmentType : UserCourseEnrollment.EnrollmentType.ASSIGNED
                        );
                        enrolled++;
                    }
                } catch (Exception e) {
                    errors++;
                }
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("courseId", courseId);
            result.put("courseName", course.getName());
            result.put("totalUsers", allUsers.size());
            result.put("newlyEnrolled", enrolled);
            result.put("alreadyEnrolled", alreadyEnrolled);
            result.put("errors", errors);
            result.put("message", String.format(
                "Enrollment completed: %d newly enrolled, %d already enrolled, %d errors",
                enrolled, alreadyEnrolled, errors));
            
            return ResponseEntity.ok(new ApiResponse<>(200, result));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, null));
        }
    }
    
    /**
     * Enroll a specific user to all courses (Admin - Migration utility)
     */
    @ApiOperation(value = "Enroll user to all courses", notes = "Enroll a specific user to all courses - Migration utility (Admin only)")
    @ApiResponses(value = {
            @io.swagger.annotations.ApiResponse(code = 200, message = "Success"),
            @io.swagger.annotations.ApiResponse(code = 400, message = "Bad request"),
            @io.swagger.annotations.ApiResponse(code = 401, message = "Unauthorized"),
            @io.swagger.annotations.ApiResponse(code = 403, message = "Forbidden - Admin access required"),
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
    @PostMapping("/enroll-all/{userId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> enrollUserToAllCourses(
            @PathVariable String userId,
            @RequestParam(required = false) UserCourseEnrollment.EnrollmentType enrollmentType,
            HttpServletRequest request) {
        try {
            verifyAccess(request);
        } catch (RuntimeException e) {
            return accessDeniedResponse(e);
        }
        try {
            // Verify user exists
            User user = userService.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
            
            // Get all courses
            List<Course> allCourses = courseRepository.findAll();
            
            int enrolled = 0;
            int alreadyEnrolled = 0;
            int errors = 0;
            
            for (Course course : allCourses) {
                try {
                    if (userCourseService.isUserEnrolled(userId, course.getId())) {
                        alreadyEnrolled++;
                    } else {
                        userCourseService.enrollUserToCourse(
                            userId, 
                            course.getId(), 
                            enrollmentType != null ? enrollmentType : UserCourseEnrollment.EnrollmentType.ASSIGNED
                        );
                        enrolled++;
                    }
                } catch (Exception e) {
                    errors++;
                }
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("userId", userId);
            result.put("userEmail", user.getEmail());
            result.put("totalCourses", allCourses.size());
            result.put("newlyEnrolled", enrolled);
            result.put("alreadyEnrolled", alreadyEnrolled);
            result.put("errors", errors);
            result.put("message", String.format(
                "Enrollment completed: %d newly enrolled, %d already enrolled, %d errors",
                enrolled, alreadyEnrolled, errors));
            
            return ResponseEntity.ok(new ApiResponse<>(200, result));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, null));
        }
    }
    
    /**
     * Enroll all users to all courses (Admin - Migration utility)
     * Use with caution - this will enroll every user to every course
     */
    @ApiOperation(value = "Enroll all users to all courses", notes = "Enroll all users to all courses - Migration utility. Use with caution! (Admin only)")
    @ApiResponses(value = {
            @io.swagger.annotations.ApiResponse(code = 200, message = "Success"),
            @io.swagger.annotations.ApiResponse(code = 400, message = "Bad request"),
            @io.swagger.annotations.ApiResponse(code = 401, message = "Unauthorized"),
            @io.swagger.annotations.ApiResponse(code = 403, message = "Forbidden - Admin access required"),
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
    @PostMapping("/enroll-all-to-all")
    public ResponseEntity<ApiResponse<Map<String, Object>>> enrollAllUsersToAllCourses(
            @RequestParam(required = false) UserCourseEnrollment.EnrollmentType enrollmentType,
            HttpServletRequest request) {
        try {
            verifyAccess(request);
        } catch (RuntimeException e) {
            return accessDeniedResponse(e);
        }
        try {
            // Get all users and courses (using a large page size to get all)
            List<User> allUsers = userService.findAll(0, 10000, "createdAt", false).getContent();
            List<Course> allCourses = courseRepository.findAll();
            
            int totalEnrollments = 0;
            int alreadyEnrolled = 0;
            int errors = 0;
            
            for (User user : allUsers) {
                for (Course course : allCourses) {
                    try {
                        if (userCourseService.isUserEnrolled(user.getId(), course.getId())) {
                            alreadyEnrolled++;
                        } else {
                            userCourseService.enrollUserToCourse(
                                user.getId(), 
                                course.getId(), 
                                enrollmentType != null ? enrollmentType : UserCourseEnrollment.EnrollmentType.ASSIGNED
                            );
                            totalEnrollments++;
                        }
                    } catch (Exception e) {
                        errors++;
                    }
                }
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("totalUsers", allUsers.size());
            result.put("totalCourses", allCourses.size());
            result.put("newlyEnrolled", totalEnrollments);
            result.put("alreadyEnrolled", alreadyEnrolled);
            result.put("errors", errors);
            result.put("message", String.format(
                "Bulk enrollment completed: %d newly enrolled, %d already enrolled, %d errors",
                totalEnrollments, alreadyEnrolled, errors));
            
            return ResponseEntity.ok(new ApiResponse<>(200, result));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, null));
        }
    }
    
    /**
     * Get enrollment statistics
     */
    @ApiOperation(value = "Get enrollment statistics", notes = "Get enrollment statistics (Admin only)")
    @ApiResponses(value = {
            @io.swagger.annotations.ApiResponse(code = 200, message = "Success"),
            @io.swagger.annotations.ApiResponse(code = 400, message = "Bad request"),
            @io.swagger.annotations.ApiResponse(code = 401, message = "Unauthorized"),
            @io.swagger.annotations.ApiResponse(code = 403, message = "Forbidden - Admin access required"),
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
    @GetMapping("/enrollments/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getEnrollmentStats(HttpServletRequest request) {
        try {
            verifyAccess(request);
        } catch (RuntimeException e) {
            return accessDeniedResponse(e);
        }
        try {
            long totalEnrollments = enrollmentRepository.count();
            long activeEnrollments = enrollmentRepository
                .findAll()
                .stream()
                .filter(e -> e.getStatus() == UserCourseEnrollment.EnrollmentStatus.ACTIVE)
                .count();
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalEnrollments", totalEnrollments);
            stats.put("activeEnrollments", activeEnrollments);
            stats.put("inactiveEnrollments", totalEnrollments - activeEnrollments);
            
            return ResponseEntity.ok(new ApiResponse<>(200, stats));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, null));
        }
    }

    private void verifyAccess(HttpServletRequest request) {
        String userId = skillamaAuthSupport.resolveUserIdFromRequest(request);
        User user = userService.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (user.getRole() != User.UserRole.ADMIN
                && user.getRole() != User.UserRole.OWNER
                && user.getRole() != User.UserRole.TESTER) {
            throw new RuntimeException("Access denied. ADMIN, OWNER, or TESTER role required.");
        }
    }

    private <T> ResponseEntity<ApiResponse<T>> accessDeniedResponse(RuntimeException e) {
        String msg = e.getMessage() != null ? e.getMessage() : "";
        if (msg.contains("Access denied")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, null));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
    }
}


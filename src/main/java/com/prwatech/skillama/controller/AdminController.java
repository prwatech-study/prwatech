package com.prwatech.skillama.controller;

import com.prwatech.authentication.security.JwtUtils;
import com.prwatech.common.Constants;
import com.prwatech.skillama.dto.*;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.service.AdminService;
import com.prwatech.skillama.service.CourseService;
import com.prwatech.skillama.service.UserService;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * Admin Controller for Skillama Admin Panel
 * Note: Add @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')") in production for security
 */
@RestController
@RequestMapping("/skillama/api/admin")
@RequiredArgsConstructor
public class AdminController {
    
    private final AdminService adminService;
    private final UserService userService;
    private final CourseService courseService;
    private final JwtUtils jwtUtils;
    
    // ========== Authentication & Authorization ==========
    
    /**
     * Check Admin Access
     */
    @ApiOperation(value = "Check admin access", notes = "Check if the authenticated user has admin access")
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
    @GetMapping("/check-access")
    public ResponseEntity<ApiResponse<AdminAccessDTO>> checkAccess(HttpServletRequest request) {
        try {
            String userId = extractUserIdFromRequest(request);
            AdminAccessDTO access = adminService.checkAdminAccess(userId);
            return ResponseEntity.ok(new ApiResponse<>(200, access));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, null));
        }
    }
    
    // ========== User Management ==========
    
    /**
     * Get All Users (Admin)
     */
    @ApiOperation(value = "Get all users", notes = "Get paginated list of users with optional search and filter (Admin only)")
    @ApiResponses(value = {
            @io.swagger.annotations.ApiResponse(code = 200, message = "Success"),
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
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<Page<UserDTO>>> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) User.UserRole role,
            @RequestParam(required = false) Boolean active,
            HttpServletRequest request) {
        try {
            extractUserIdFromRequest(request); // Verify authentication
            Page<UserDTO> users = adminService.getUsers(page, size, search, role, active);
            return ResponseEntity.ok(new ApiResponse<>(200, users));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, null));
        }
    }
    
    /**
     * Create User (Admin)
     */
    @ApiOperation(value = "Create user", notes = "Create a new user (Admin only)")
    @ApiResponses(value = {
            @io.swagger.annotations.ApiResponse(code = 201, message = "User created successfully"),
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
    @PostMapping("/users")
    public ResponseEntity<ApiResponse<UserDTO>> createUser(
            @RequestBody CreateUserRequest request,
            HttpServletRequest httpRequest) {
        try {
            String createdBy = extractUserIdFromRequest(httpRequest);
            UserDTO user = adminService.createUser(request, createdBy);
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(201, user));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, null));
        }
    }
    
    /**
     * Create Admin User (Owner Only)
     */
    @ApiOperation(value = "Create admin user", notes = "Create a new admin user (Owner only)")
    @ApiResponses(value = {
            @io.swagger.annotations.ApiResponse(code = 201, message = "Admin user created successfully"),
            @io.swagger.annotations.ApiResponse(code = 401, message = "Unauthorized"),
            @io.swagger.annotations.ApiResponse(code = 403, message = "Forbidden - Owner access required"),
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
    @PostMapping("/users/create-admin")
    public ResponseEntity<ApiResponse<UserDTO>> createAdmin(
            @RequestBody CreateUserRequest request,
            HttpServletRequest httpRequest) {
        try {
            String createdBy = extractUserIdFromRequest(httpRequest);
            UserDTO user = adminService.createAdminUser(request, createdBy);
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(201, user));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiResponse<>(403, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, null));
        }
    }
    
    /**
     * Update User (Admin)
     */
    @ApiOperation(value = "Update user", notes = "Update user information (Admin only)")
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
    @PutMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<UserDTO>> updateUser(
            @PathVariable String userId,
            @RequestBody UpdateUserRequest request,
            HttpServletRequest httpRequest) {
        try {
            String updatedBy = extractUserIdFromRequest(httpRequest);
            UserDTO user = adminService.updateUser(userId, request, updatedBy);
            return ResponseEntity.ok(new ApiResponse<>(200, user));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, null));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, null));
        }
    }
    
    /**
     * Delete User (Admin) - Soft delete
     */
    @ApiOperation(value = "Delete user", notes = "Soft delete a user (Admin only)")
    @ApiResponses(value = {
            @io.swagger.annotations.ApiResponse(code = 200, message = "Success"),
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
    @DeleteMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable String userId) {
        try {
            adminService.deleteUser(userId);
            return ResponseEntity.ok(new ApiResponse<>(200, null));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, null));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, null));
        }
    }
    
    // ========== Course Management ==========
    // Note: Reusing existing CourseController endpoints with /api/admin prefix
    // GET /api/admin/courses - Use existing GET /skillama/courses
    // POST /api/admin/courses - Use existing POST /skillama/courses
    // PUT /api/admin/courses/{id} - Use existing PUT /skillama/courses/{id}
    // DELETE /api/admin/courses/{id} - Use existing DELETE /skillama/courses/{id}
    
    /**
     * Get All Courses (Admin) - Wrapper for existing endpoint
     */
    @ApiOperation(value = "Get all courses", notes = "Get paginated list of all courses (Admin only)")
    @ApiResponses(value = {
            @io.swagger.annotations.ApiResponse(code = 200, message = "Success"),
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
    @GetMapping("/courses")
    public ResponseEntity<ApiResponse<Page<Course>>> getCourses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String order,
            HttpServletRequest request) {
        try {
            extractUserIdFromRequest(request); // Verify authentication
            boolean desc = order.equalsIgnoreCase("desc");
            Page<Course> courses = courseService.findAll(page, size, sortBy, desc);
            return ResponseEntity.ok(new ApiResponse<>(200, courses));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, null));
        }
    }
    
    /**
     * Create Course (Admin) - Wrapper for existing endpoint
     */
    @ApiOperation(value = "Create course", notes = "Create a new course (Admin only)")
    @ApiResponses(value = {
            @io.swagger.annotations.ApiResponse(code = 201, message = "Course created successfully"),
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
    @PostMapping("/courses")
    public ResponseEntity<ApiResponse<Course>> createCourse(
            @RequestBody Course course,
            HttpServletRequest request) {
        try {
            extractUserIdFromRequest(request); // Verify authentication
            Course created = courseService.create(course);
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(201, created));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, null));
        }
    }
    
    /**
     * Update Course (Admin) - Wrapper for existing endpoint
     */
    @ApiOperation(value = "Update course", notes = "Update course information (Admin only)")
    @ApiResponses(value = {
            @io.swagger.annotations.ApiResponse(code = 200, message = "Success"),
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
    @PutMapping("/courses/{courseId}")
    public ResponseEntity<ApiResponse<Course>> updateCourse(
            @PathVariable String courseId,
            @RequestBody Course course,
            HttpServletRequest request) {
        try {
            extractUserIdFromRequest(request); // Verify authentication
            Course updated = courseService.update(courseId, course);
            if (updated == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(404, null));
            }
            return ResponseEntity.ok(new ApiResponse<>(200, updated));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, null));
        }
    }
    
    /**
     * Delete Course (Admin) - Wrapper for existing endpoint
     */
    @ApiOperation(value = "Delete course", notes = "Delete a course (Admin only)")
    @ApiResponses(value = {
            @io.swagger.annotations.ApiResponse(code = 200, message = "Success"),
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
    @DeleteMapping("/courses/{courseId}")
    public ResponseEntity<ApiResponse<Void>> deleteCourse(
            @PathVariable String courseId,
            HttpServletRequest request) {
        try {
            extractUserIdFromRequest(request); // Verify authentication
            courseService.delete(courseId);
            return ResponseEntity.ok(new ApiResponse<>(200, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, null));
        }
    }
    
    // ========== Course Assignment ==========
    
    /**
     * Assign Courses to User
     */
    @ApiOperation(value = "Assign courses to user", notes = "Assign multiple courses to a user (Admin only)")
    @ApiResponses(value = {
            @io.swagger.annotations.ApiResponse(code = 200, message = "Success"),
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
    @PostMapping("/assignments/assign")
    public ResponseEntity<ApiResponse<AssignmentResponseDTO>> assignCourses(
            @RequestBody AssignCoursesRequest request,
            HttpServletRequest httpRequest) {
        try {
            extractUserIdFromRequest(httpRequest); // Verify authentication
            AssignmentResponseDTO response = adminService.assignCourses(
                request.getUserId(), request.getCourseIds());
            return ResponseEntity.ok(new ApiResponse<>(200, response));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, null));
        }
    }
    
    /**
     * Unassign Course from User
     */
    @ApiOperation(value = "Unassign course from user", notes = "Unassign a course from a user (Admin only)")
    @ApiResponses(value = {
            @io.swagger.annotations.ApiResponse(code = 200, message = "Success"),
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
    @DeleteMapping("/assignments/unassign")
    public ResponseEntity<ApiResponse<Void>> unassignCourse(
            @RequestBody UnassignCourseRequest request,
            HttpServletRequest httpRequest) {
        try {
            extractUserIdFromRequest(httpRequest); // Verify authentication
            adminService.unassignCourse(request.getUserId(), request.getCourseId());
            return ResponseEntity.ok(new ApiResponse<>(200, null));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, null));
        }
    }
    
    /**
     * Get User Assignments
     */
    @ApiOperation(value = "Get user assignments", notes = "Get all course assignments for a specific user (Admin only)")
    @ApiResponses(value = {
            @io.swagger.annotations.ApiResponse(code = 200, message = "Success"),
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
    @GetMapping("/assignments/user/{userId}")
    public ResponseEntity<ApiResponse<UserAssignmentsDTO>> getUserAssignments(
            @PathVariable String userId,
            HttpServletRequest request) {
        try {
            extractUserIdFromRequest(request); // Verify authentication
            UserAssignmentsDTO assignments = adminService.getUserAssignments(userId);
            return ResponseEntity.ok(new ApiResponse<>(200, assignments));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, null));
        }
    }
    
    /**
     * Get Course Assignments
     */
    @ApiOperation(value = "Get course assignments", notes = "Get all user assignments for a specific course (Admin only)")
    @ApiResponses(value = {
            @io.swagger.annotations.ApiResponse(code = 200, message = "Success"),
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
    @GetMapping("/assignments/course/{courseId}")
    public ResponseEntity<ApiResponse<CourseAssignmentsDTO>> getCourseAssignments(
            @PathVariable String courseId,
            HttpServletRequest request) {
        try {
            extractUserIdFromRequest(request); // Verify authentication
            CourseAssignmentsDTO assignments = adminService.getCourseAssignments(courseId);
            return ResponseEntity.ok(new ApiResponse<>(200, assignments));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, null));
        }
    }
    
    // ========== Analytics ==========
    
    /**
     * Get Dashboard Statistics
     */
    @ApiOperation(value = "Get dashboard statistics", notes = "Get dashboard statistics and analytics (Admin only)")
    @ApiResponses(value = {
            @io.swagger.annotations.ApiResponse(code = 200, message = "Success"),
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
    @GetMapping("/analytics/dashboard")
    public ResponseEntity<ApiResponse<DashboardStatsDTO>> getDashboardStats(
            HttpServletRequest request) {
        try {
            extractUserIdFromRequest(request); // Verify authentication
            DashboardStatsDTO stats = adminService.getDashboardStatistics();
            return ResponseEntity.ok(new ApiResponse<>(200, stats));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, null));
        }
    }
    
    /**
     * Get User Progress Report
     */
    @ApiOperation(value = "Get user progress report", notes = "Get progress report for a specific user (Admin only)")
    @ApiResponses(value = {
            @io.swagger.annotations.ApiResponse(code = 200, message = "Success"),
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
    @GetMapping("/analytics/users/{userId}/progress")
    public ResponseEntity<ApiResponse<UserAssignmentsDTO>> getUserProgress(
            @PathVariable String userId,
            HttpServletRequest request) {
        try {
            extractUserIdFromRequest(request); // Verify authentication
            // Reuse getUserAssignments which includes progress
            UserAssignmentsDTO assignments = adminService.getUserAssignments(userId);
            return ResponseEntity.ok(new ApiResponse<>(200, assignments));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, null));
        }
    }
    
    /**
     * Get Course Analytics
     */
    @ApiOperation(value = "Get course analytics", notes = "Get analytics for a specific course (Admin only)")
    @ApiResponses(value = {
            @io.swagger.annotations.ApiResponse(code = 200, message = "Success"),
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
    @GetMapping("/analytics/courses/{courseId}")
    public ResponseEntity<ApiResponse<CourseAnalyticsDTO>> getCourseAnalytics(
            @PathVariable String courseId,
            HttpServletRequest request) {
        try {
            extractUserIdFromRequest(request); // Verify authentication
            CourseAnalyticsDTO analytics = adminService.getCourseAnalytics(courseId);
            return ResponseEntity.ok(new ApiResponse<>(200, analytics));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, null));
        }
    }
    
    /**
     * Extracts userId from JWT token in Authorization header
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


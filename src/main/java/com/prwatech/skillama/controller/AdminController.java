package com.prwatech.skillama.controller;

import com.prwatech.authentication.security.JwtUtils;
import com.prwatech.common.Constants;
import com.prwatech.skillama.dto.*;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.script.GuestCourseMigrationScript;
import com.prwatech.skillama.model.Review;
import com.prwatech.skillama.model.SalesLead;
import com.prwatech.skillama.service.AdminAuditService;
import com.prwatech.skillama.service.AdminService;
import com.prwatech.skillama.service.CourseService;
import com.prwatech.skillama.service.FreemiumService;
import com.prwatech.skillama.service.PlatformDemoVideoService;
import com.prwatech.skillama.service.NotificationSettingsService;
import com.prwatech.skillama.service.ReferralShareService;
import com.prwatech.skillama.service.ReviewService;
import com.prwatech.skillama.service.SalesLeadService;
import com.prwatech.skillama.service.UpgradeRequestService;
import com.prwatech.skillama.service.UserService;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
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
import java.util.Map;

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
    private final GuestCourseMigrationScript guestCourseMigrationScript;
    private final FreemiumService freemiumService;
    private final SalesLeadService salesLeadService;
    private final ReviewService reviewService;
    private final PlatformDemoVideoService platformDemoVideoService;
    private final ReferralShareService referralShareService;
    private final NotificationSettingsService notificationSettingsService;
    private final AdminAuditService adminAuditService;
    private final UpgradeRequestService upgradeRequestService;

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
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) User.PlanTier planTier,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            HttpServletRequest request) {
        try {
            extractUserIdFromRequest(request);
            java.time.LocalDateTime from = fromDate != null ? java.time.LocalDate.parse(fromDate).atStartOfDay() : null;
            java.time.LocalDateTime to = toDate != null ? java.time.LocalDate.parse(toDate).atTime(23, 59, 59) : null;
            Page<UserDTO> users = adminService.getUsers(page, size, search, role, active, phone, planTier, from, to);
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
    public ResponseEntity<?> createUser(
            @RequestBody CreateUserRequest request,
            HttpServletRequest httpRequest) {
        try {
            String createdBy = extractUserIdFromRequest(httpRequest);
            UserDTO user = adminService.createUser(request, createdBy);
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(201, user));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("status", "error", "message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("status", "error", "message", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("status", "error", "message", e.getMessage()));
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
     * Promote User to Admin by Email (Owner Only)
     */
    @ApiOperation(value = "Promote user to admin by email", notes = "Promote a normal user to ADMIN role using their email address (Owner only)")
    @ApiResponses(value = {
            @io.swagger.annotations.ApiResponse(code = 200, message = "User promoted successfully"),
            @io.swagger.annotations.ApiResponse(code = 400, message = "Bad request"),
            @io.swagger.annotations.ApiResponse(code = 401, message = "Unauthorized"),
            @io.swagger.annotations.ApiResponse(code = 403, message = "Forbidden - Owner access required"),
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
    @PutMapping("/users/{userId}/promote-to-admin")
    public ResponseEntity<ApiResponse<UserDTO>> promoteUserToAdminById(
            @PathVariable String userId,
            HttpServletRequest httpRequest) {
        try {
            String updatedBy = extractUserIdFromRequest(httpRequest);
            UserDTO user = adminService.promoteUserToAdmin(userId, updatedBy);
            return ResponseEntity.ok(new ApiResponse<>(200, user));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, null));
        } catch (RuntimeException e) {
            if (e.getMessage() != null
                    && (e.getMessage().contains("Only OWNER") || e.getMessage().contains("Owner access"))) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ApiResponse<>(403, null));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, null));
        }
    }

    @PutMapping("/users/promote-to-admin")
    public ResponseEntity<ApiResponse<UserDTO>> promoteUserToAdminByEmail(
            @RequestParam("email") String email,
            HttpServletRequest httpRequest) {
        try {
            String updatedBy = extractUserIdFromRequest(httpRequest);
            adminService.requireOwner(updatedBy);
            UserDTO user = adminService.updateUserRoleByEmail(email, User.UserRole.ADMIN, updatedBy);
            return ResponseEntity.ok(new ApiResponse<>(200, user));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, null));
        } catch (RuntimeException e) {
            if (e.getMessage() != null
                    && (e.getMessage().contains("Only OWNER") || e.getMessage().contains("Owner access"))) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ApiResponse<>(403, null));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, null));
        }
    }

    @PutMapping("/users/{userId}/demote-from-admin")
    public ResponseEntity<ApiResponse<UserDTO>> demoteAdminToUser(
            @PathVariable String userId,
            HttpServletRequest httpRequest) {
        try {
            String updatedBy = extractUserIdFromRequest(httpRequest);
            UserDTO user = adminService.demoteAdminToUser(userId, updatedBy);
            return ResponseEntity.ok(new ApiResponse<>(200, user));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, null));
        } catch (RuntimeException e) {
            if (e.getMessage() != null
                    && (e.getMessage().contains("Only OWNER") || e.getMessage().contains("Owner access"))) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ApiResponse<>(403, null));
            }
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
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @PathVariable String userId,
            @RequestParam(defaultValue = "false") boolean hard,
            @RequestParam(required = false) String reason,
            HttpServletRequest httpRequest) {
        try {
            String deletedBy = extractUserIdFromRequest(httpRequest);
            adminService.deleteUser(userId, deletedBy, hard, reason);
            return ResponseEntity.ok(new ApiResponse<>(200, null));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, null));
        } catch (RuntimeException e) {
            if (e.getMessage() != null && (e.getMessage().contains("OWNER") || e.getMessage().contains("ADMIN"))) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ApiResponse<>(403, null));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, null));
        }
    }

    @PutMapping("/users/{userId}/password")
    public ResponseEntity<ApiResponse<Void>> resetAdminPassword(
            @PathVariable String userId,
            @RequestBody ResetAdminPasswordRequestDTO body,
            HttpServletRequest httpRequest) {
        try {
            String ownerId = extractUserIdFromRequest(httpRequest);
            adminService.resetAdminPassword(ownerId, userId, body.getNewPassword());
            return ResponseEntity.ok(new ApiResponse<>(200, null));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(404, null));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
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
            String adminId = extractUserIdFromRequest(request);
            Course created = courseService.create(course);
            adminAuditService.log(adminId, AdminAuditService.COURSE_CREATE, "COURSE", created.getId(),
                    "Created course " + created.getName(), null);
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
            String adminId = extractUserIdFromRequest(request);
            Course updated = courseService.update(courseId, course);
            if (updated == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(404, null));
            }
            adminAuditService.log(adminId, AdminAuditService.COURSE_UPDATE, "COURSE", courseId,
                    "Updated course " + updated.getName(), null);
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
            String actorId = extractUserIdFromRequest(request);
            adminService.requireOwner(actorId);
            courseService.softDelete(courseId, actorId);
            adminAuditService.log(actorId, AdminAuditService.COURSE_DELETE, "COURSE", courseId,
                    "Archived (soft-deleted) course " + courseId, null);
            return ResponseEntity.ok(new ApiResponse<>(200, null));
        } catch (com.prwatech.common.exception.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, null));
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("Authorization")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse<>(401, null));
            }
            if (e.getMessage() != null && e.getMessage().contains("Owner access")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ApiResponse<>(403, null));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, null));
        }
    }

    @ApiImplicitParams({
            @ApiImplicitParam(
                    name = Constants.AUTH,
                    value = Constants.TOKEN_TYPE,
                    required = true,
                    dataType = Constants.AUTH_DATA_TYPE,
                    paramType = Constants.AUTH_PARAM_TYPE)
    })
    @GetMapping("/courses/archived")
    public ResponseEntity<ApiResponse<List<Course>>> getArchivedCourses(HttpServletRequest request) {
        try {
            String actorId = extractUserIdFromRequest(request);
            adminService.requireOwner(actorId);
            return ResponseEntity.ok(new ApiResponse<>(200, courseService.findAllDeleted()));
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("Owner access")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, null));
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    @ApiImplicitParams({
            @ApiImplicitParam(
                    name = Constants.AUTH,
                    value = Constants.TOKEN_TYPE,
                    required = true,
                    dataType = Constants.AUTH_DATA_TYPE,
                    paramType = Constants.AUTH_PARAM_TYPE)
    })
    @PostMapping("/courses/{courseId}/restore")
    public ResponseEntity<ApiResponse<Course>> restoreCourse(
            @PathVariable String courseId,
            HttpServletRequest request) {
        try {
            String actorId = extractUserIdFromRequest(request);
            adminService.requireOwner(actorId);
            Course restored = courseService.restore(courseId, actorId);
            adminAuditService.log(actorId, AdminAuditService.COURSE_RESTORE, "COURSE", courseId,
                    "Restored archived course " + restored.getName(), null);
            return ResponseEntity.ok(new ApiResponse<>(200, restored));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(404, null));
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("Owner access")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, null));
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }
    
    // ========== Guest Course Management ==========
    
    /**
     * Setup Guest Course (Public - No Auth Required)
     * Automatically sets up a guest course if one doesn't exist
     * This endpoint is public to allow first-time setup without authentication
     */
    @ApiOperation(value = "Setup guest course", notes = "Automatically configure a guest course for non-logged-in users. Public endpoint for first-time setup.")
    @ApiResponses(value = {
            @io.swagger.annotations.ApiResponse(code = 200, message = "Guest course setup completed"),
            @io.swagger.annotations.ApiResponse(code = 500, message = "Internal server error")
    })
    @PostMapping("/courses/setup-guest-course")
    public ResponseEntity<ApiResponse<String>> setupGuestCourse() {
        try {
            guestCourseMigrationScript.setupGuestCourse();
            return ResponseEntity.ok(new ApiResponse<>(200, "Guest course setup completed successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(500, "Error setting up guest course: " + e.getMessage()));
        }
    }
    
    /**
     * Set Course as Guest Course (Admin)
     * Sets a specific course as the guest course
     */
    @ApiOperation(value = "Set course as guest course", notes = "Set a specific course as the guest course for non-logged-in users (Admin only)")
    @ApiResponses(value = {
            @io.swagger.annotations.ApiResponse(code = 200, message = "Course set as guest course successfully"),
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
    @PutMapping("/courses/{courseId}/set-guest")
    public ResponseEntity<ApiResponse<Course>> setCourseAsGuest(
            @PathVariable String courseId,
            HttpServletRequest request) {
        try {
            extractUserIdFromRequest(request); // Verify authentication
            boolean success = guestCourseMigrationScript.setCourseAsGuestCourse(courseId);
            if (!success) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(404, null));
            }
            Course guestCourse = courseService.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));
            return ResponseEntity.ok(new ApiResponse<>(200, guestCourse));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(500, null));
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
            String assignedBy = extractUserIdFromRequest(httpRequest);
            AssignmentResponseDTO response = adminService.assignCourses(
                request.getUserId(), request.getCourseIds(), assignedBy);
            return ResponseEntity.ok(new ApiResponse<>(200, response));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, null));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, null));
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("Authorization")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse<>(401, null));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, null));
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
            @RequestBody(required = false) UnassignCourseRequest request,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String courseId,
            HttpServletRequest httpRequest) {
        UnassignCourseRequest body = resolveUnassignRequest(request, userId, courseId);
        try {
            String unassignedBy = extractUserIdFromRequest(httpRequest);
            adminService.unassignCourse(body.getUserId(), body.getCourseId(), unassignedBy);
            return ResponseEntity.ok(new ApiResponse<>(200, null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, null));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, null));
        }
    }

    private static UnassignCourseRequest resolveUnassignRequest(
            UnassignCourseRequest request, String userId, String courseId) {
        if (request != null && request.getUserId() != null && request.getCourseId() != null) {
            return request;
        }
        if (userId != null && courseId != null) {
            return new UnassignCourseRequest(userId, courseId);
        }
        throw new IllegalArgumentException("userId and courseId are required");
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
    /**
     * Update user plan tier (demo / testing): FREEMIUM, PAID, or ENTERPRISE.
     */
    @PutMapping("/users/{userId}/plan")
    public ResponseEntity<ApiResponse<FreemiumStatusDTO>> updateUserPlan(
            @PathVariable String userId,
            @RequestBody UpdateUserPlanRequestDTO request,
            HttpServletRequest httpRequest) {
        try {
            String adminId = extractUserIdFromRequest(httpRequest);
            if (request.getPlanTier() == null) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse<>(400, null));
            }
            FreemiumStatusDTO status = freemiumService.updateUserPlan(userId, request.getPlanTier());
            adminAuditService.log(adminId, AdminAuditService.PLAN_UPDATE, "USER", userId,
                    "Plan set to " + request.getPlanTier() + " for user " + userId,
                    request.getReason());
            return ResponseEntity.ok(new ApiResponse<>(200, status));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, null));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(404, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    /**
     * Shortcut: move user to paid (premium) plan for demos.
     */
    @PostMapping("/users/{userId}/plan/premium")
    public ResponseEntity<ApiResponse<FreemiumStatusDTO>> upgradeUserToPremium(
            @PathVariable String userId,
            HttpServletRequest httpRequest) {
        try {
            String adminId = extractUserIdFromRequest(httpRequest);
            FreemiumStatusDTO status = freemiumService.updateUserPlan(userId, User.PlanTier.PAID);
            adminAuditService.log(adminId, AdminAuditService.PLAN_UPDATE, "USER", userId,
                    "Removed freemium — upgraded to PAID", null);
            return ResponseEntity.ok(new ApiResponse<>(200, status));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(404, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    @PostMapping("/users/{userId}/credits/adjust")
    public ResponseEntity<ApiResponse<CreditAdjustmentLogDTO>> adjustUserCredits(
            @PathVariable String userId,
            @RequestBody CreditAdjustRequestDTO body,
            HttpServletRequest httpRequest) {
        try {
            String adminId = extractUserIdFromRequest(httpRequest);
            adminService.requireAdminOrOwner(adminId);
            CreditAdjustmentLogDTO log = freemiumService.adjustQueryCredits(userId, body, adminId);
            adminAuditService.log(adminId, AdminAuditService.CREDIT_ADJUST, "USER", userId,
                    "Credit adjust delta=" + body.getDelta() + " reason=" + body.getReason(), null);
            return ResponseEntity.ok(new ApiResponse<>(200, log));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, null));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(404, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    @GetMapping("/upgrade-requests")
    public ResponseEntity<ApiResponse<Page<UpgradeRequestDTO>>> listUpgradeRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) com.prwatech.skillama.model.UpgradeRequest.RequestStatus status,
            @RequestParam(required = false) String search,
            HttpServletRequest request) {
        try {
            extractUserIdFromRequest(request);
            return ResponseEntity.ok(new ApiResponse<>(200,
                    upgradeRequestService.list(page, size, status, search)));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    @PatchMapping("/upgrade-requests/{requestId}")
    public ResponseEntity<ApiResponse<UpgradeRequestDTO>> updateUpgradeRequest(
            @PathVariable String requestId,
            @RequestBody UpdateUpgradeRequestDTO body,
            HttpServletRequest request) {
        try {
            String adminId = extractUserIdFromRequest(request);
            return ResponseEntity.ok(new ApiResponse<>(200,
                    upgradeRequestService.update(requestId, body, adminId)));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(404, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    /**
     * OWNER: align freemium users to product default 50 queries (70 with referral) and all core modules.
     */
    @PostMapping("/maintenance/normalize-freemium-limits")
    public ResponseEntity<ApiResponse<Map<String, Object>>> normalizeFreemiumLimits(
            HttpServletRequest request) {
        try {
            String adminId = extractUserIdFromRequest(request);
            adminService.requireOwner(adminId);
            int count = freemiumService.normalizeFreemiumCreditLimits();
            adminAuditService.log(adminId, AdminAuditService.PLAN_UPDATE, "SYSTEM", "freemium-limits",
                    "Normalized freemium query limits for " + count + " users", null);
            return ResponseEntity.ok(new ApiResponse<>(200, Map.of("usersUpdated", count)));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<ApiResponse<Page<AdminAuditLogDTO>>> listAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String actorId,
            HttpServletRequest request) {
        try {
            extractUserIdFromRequest(request);
            return ResponseEntity.ok(new ApiResponse<>(200,
                    adminAuditService.list(page, size, action, actorId)));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    /**
     * Platform onboarding demo video (admin).
     */
    @GetMapping("/platform/demo-video")
    public ResponseEntity<ApiResponse<DemoVideoDTO>> getDemoVideoConfig(HttpServletRequest request) {
        try {
            extractUserIdFromRequest(request);
            return ResponseEntity.ok(new ApiResponse<>(200, platformDemoVideoService.getPublicConfig()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    @PostMapping(value = "/platform/demo-video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DemoVideoDTO>> uploadDemoVideo(
            @RequestParam("video") MultipartFile video,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String description,
            HttpServletRequest request) {
        try {
            String adminUserId = extractUserIdFromRequest(request);
            DemoVideoDTO dto = platformDemoVideoService.upload(video, title, description, adminUserId);
            return ResponseEntity.ok(new ApiResponse<>(200, dto));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    /**
     * Point demo video at an external URL: direct HTTPS video file (.mp4 etc.), YouTube / youtu.be / Shorts,
     * other embeddable HTTPS player URLs, or raw {@code <iframe ... src="https://...">} HTML.
     */
    @PutMapping("/platform/demo-video/url")
    public ResponseEntity<ApiResponse<DemoVideoDTO>> setDemoVideoUrl(
            @RequestBody DemoVideoUrlRequestDTO body,
            HttpServletRequest request) {
        try {
            String adminUserId = extractUserIdFromRequest(request);
            DemoVideoDTO dto = platformDemoVideoService.saveFromUrl(
                    body.getVideoUrl(), body.getTitle(), body.getDescription(), adminUserId);
            return ResponseEntity.ok(new ApiResponse<>(200, dto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    @PutMapping("/platform/demo-video/metadata")
    public ResponseEntity<ApiResponse<DemoVideoDTO>> updateDemoVideoMetadata(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String description,
            HttpServletRequest request) {
        try {
            String adminUserId = extractUserIdFromRequest(request);
            return ResponseEntity.ok(new ApiResponse<>(
                    200, platformDemoVideoService.updateMetadata(title, description, adminUserId)));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    @DeleteMapping("/platform/demo-video")
    public ResponseEntity<ApiResponse<Void>> deleteDemoVideo(HttpServletRequest request) {
        try {
            String adminUserId = extractUserIdFromRequest(request);
            platformDemoVideoService.remove(adminUserId);
            return ResponseEntity.ok(new ApiResponse<>(200, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    @GetMapping("/platform/referral-share")
    public ResponseEntity<ApiResponse<ReferralShareConfigDTO>> getReferralShareAdminConfig(HttpServletRequest request) {
        try {
            extractUserIdFromRequest(request);
            return ResponseEntity.ok(new ApiResponse<>(200, referralShareService.getPublicConfig()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    @PutMapping("/platform/referral-share")
    public ResponseEntity<ApiResponse<ReferralShareConfigDTO>> updateReferralShareConfig(
            @RequestBody UpdateReferralShareConfigDTO body,
            HttpServletRequest request) {
        try {
            String adminUserId = extractUserIdFromRequest(request);
            return ResponseEntity.ok(
                    new ApiResponse<>(200, referralShareService.updateConfig(body, adminUserId)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    @GetMapping("/platform/notification-settings")
    public ResponseEntity<ApiResponse<NotificationSettingsDTO>> getNotificationSettings(HttpServletRequest request) {
        try {
            extractUserIdFromRequest(request);
            return ResponseEntity.ok(
                    new ApiResponse<>(200, notificationSettingsService.getAdminSettings()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    @PutMapping("/platform/notification-settings")
    public ResponseEntity<ApiResponse<NotificationSettingsDTO>> updateNotificationSettings(
            @RequestBody UpdateNotificationSettingsDTO body,
            HttpServletRequest request) {
        try {
            String adminUserId = extractUserIdFromRequest(request);
            return ResponseEntity.ok(
                    new ApiResponse<>(200, notificationSettingsService.updateSettings(body, adminUserId)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    /**
     * List user feedback / reviews for admin triage.
     */
    @GetMapping("/reviews")
    public ResponseEntity<ApiResponse<Page<Review>>> listReviews(
            @RequestParam(required = false) String courseId,
            @RequestParam(required = false) Review.ReviewStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        try {
            extractUserIdFromRequest(request);
            Page<Review> reviews = reviewService.getReviewsForAdmin(courseId, status, page, size);
            return ResponseEntity.ok(new ApiResponse<>(200, reviews));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    /**
     * Reply to user feedback; emails the user and stores team reply on the review.
     */
    @PutMapping("/reviews/{reviewId}/reply")
    public ResponseEntity<ApiResponse<Review>> replyToReview(
            @PathVariable String reviewId,
            @RequestBody AdminReviewReplyRequestDTO body,
            HttpServletRequest request) {
        try {
            String adminUserId = extractUserIdFromRequest(request);
            Review updated = reviewService.adminReply(reviewId, body, adminUserId);
            return ResponseEntity.ok(new ApiResponse<>(200, updated));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(404, null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    /**
     * List sales-interest leads (marketing opt-in only).
     */
    @GetMapping("/leads/sales-interest")
    public ResponseEntity<ApiResponse<Page<SalesLead>>> listSalesLeads(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        try {
            extractUserIdFromRequest(request);
            return ResponseEntity.ok(new ApiResponse<>(200, salesLeadService.listLeads(page, size)));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    @GetMapping("/users/{userId}/profile")
    public ResponseEntity<ApiResponse<UserAdminProfileDTO>> getUserAdminProfile(
            @PathVariable String userId,
            HttpServletRequest request) {
        try {
            extractUserIdFromRequest(request);
            return ResponseEntity.ok(new ApiResponse<>(200, adminService.getUserAdminProfile(userId)));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(404, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    @GetMapping("/users/{userId}/activity")
    public ResponseEntity<ApiResponse<UserActivityDTO>> getUserActivity(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            HttpServletRequest request) {
        try {
            extractUserIdFromRequest(request);
            return ResponseEntity.ok(new ApiResponse<>(200, adminService.getUserActivity(userId, page, size)));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(404, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    @GetMapping("/users/{userId}/progress")
    public ResponseEntity<ApiResponse<UserAssignmentsDTO>> getUserProgressByPath(
            @PathVariable String userId,
            HttpServletRequest request) {
        try {
            extractUserIdFromRequest(request);
            UserAssignmentsDTO assignments = adminService.getUserAssignments(userId);
            return ResponseEntity.ok(new ApiResponse<>(200, assignments));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(404, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

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


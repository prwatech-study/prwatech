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
import com.prwatech.skillama.service.PlatformAiSettingsService;
import com.prwatech.skillama.service.PlatformDemoVideoService;
import com.prwatech.skillama.service.NotificationSettingsService;
import com.prwatech.skillama.service.AdminPermissionService;
import com.prwatech.skillama.service.ProgressReconciliationService;
import com.prwatech.skillama.service.DemoDashboardSeedService;
import com.prwatech.skillama.service.ReferralShareService;
import com.prwatech.skillama.service.SkillamaPlatformConfigService;
import com.prwatech.skillama.model.AdminModule;
import com.prwatech.skillama.model.AdminPermissionAction;
import com.prwatech.skillama.service.ReviewService;
import com.prwatech.skillama.service.IssueReportService;
import com.prwatech.skillama.model.IssueReport;
import com.prwatech.skillama.service.SalesLeadService;
import com.prwatech.skillama.service.LmsThemeService;
import com.prwatech.skillama.service.UpgradeRequestService;
import com.prwatech.skillama.service.UserProfileService;
import com.prwatech.skillama.service.UserService;
import com.prwatech.skillama.service.SkillamaAuthSupport;
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
    private final IssueReportService issueReportService;
    private final PlatformDemoVideoService platformDemoVideoService;
    private final PlatformAiSettingsService platformAiSettingsService;
    private final ReferralShareService referralShareService;
    private final NotificationSettingsService notificationSettingsService;
    private final AdminAuditService adminAuditService;
    private final UpgradeRequestService upgradeRequestService;
    private final LmsThemeService lmsThemeService;
    private final SkillamaPlatformConfigService platformConfigService;
    private final AdminPermissionService adminPermissionService;
    private final DemoDashboardSeedService demoDashboardSeedService;
    private final UserProfileService userProfileService;
    private final ProgressReconciliationService progressReconciliationService;
    private final SkillamaAuthSupport skillamaAuthSupport;
    private final com.prwatech.skillama.service.DoubtService doubtService;
    private final com.prwatech.skillama.service.ExamService examService;
    private final com.prwatech.skillama.service.ModuleQuizService moduleQuizService;

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
            AdminAccessDTO access = adminService.checkAdminAccess(userId, adminPermissionService);
            return ResponseEntity.ok(new ApiResponse<>(200, access));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, null));
        }
    }
    
    // ========== Admin access control (OWNER) ==========

    @GetMapping("/access-control/modules")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> listAssignableModules(
            HttpServletRequest request) {
        try {
            adminPermissionService.requireOwner(extractUserIdFromRequest(request));
            return ResponseEntity.ok(new ApiResponse<>(200, adminPermissionService.listAssignableModules()));
        } catch (RuntimeException e) {
            if (isOwnerForbidden(e)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, null));
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    @GetMapping("/access-control/admins")
    public ResponseEntity<ApiResponse<List<AdminUserPermissionsDTO>>> listAdminPermissions(
            HttpServletRequest request) {
        try {
            adminPermissionService.requireOwner(extractUserIdFromRequest(request));
            return ResponseEntity.ok(
                    new ApiResponse<>(200, adminPermissionService.listAdminUsersForOwner()));
        } catch (RuntimeException e) {
            if (isOwnerForbidden(e)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, null));
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    @GetMapping("/access-control/admins/{userId}")
    public ResponseEntity<ApiResponse<AdminUserPermissionsDTO>> getAdminPermissions(
            @PathVariable String userId,
            HttpServletRequest request) {
        try {
            adminPermissionService.requireOwner(extractUserIdFromRequest(request));
            return ResponseEntity.ok(
                    new ApiResponse<>(200, adminPermissionService.getAdminUserPermissions(userId)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, null));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(404, null));
        } catch (RuntimeException e) {
            if (isOwnerForbidden(e)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, null));
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    @PutMapping("/access-control/admins/{userId}")
    public ResponseEntity<ApiResponse<AdminUserPermissionsDTO>> updateAdminPermissions(
            @PathVariable String userId,
            @RequestBody UpdateAdminPermissionsRequestDTO body,
            HttpServletRequest request) {
        try {
            String ownerId = extractUserIdFromRequest(request);
            AdminUserPermissionsDTO updated = adminPermissionService.updateAdminUserPermissions(
                    userId, body, ownerId);
            return ResponseEntity.ok(new ApiResponse<>(200, updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, null));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(404, null));
        } catch (RuntimeException e) {
            if (isOwnerForbidden(e)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, null));
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
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
            assertModulePermission(request, AdminModule.USERS, AdminPermissionAction.READ);
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
            assertModulePermission(httpRequest, AdminModule.USERS, AdminPermissionAction.CREATE);
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
            assertModulePermission(httpRequest, AdminModule.USERS, AdminPermissionAction.UPDATE);
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
            assertModulePermission(httpRequest, AdminModule.USERS, AdminPermissionAction.DELETE);
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
            assertModulePermission(request, AdminModule.COURSES, AdminPermissionAction.READ);
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
            assertModulePermission(request, AdminModule.COURSES, AdminPermissionAction.CREATE);
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
            assertModulePermission(request, AdminModule.COURSES, AdminPermissionAction.UPDATE);
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
            assertModulePermission(request, AdminModule.COURSES, AdminPermissionAction.DELETE);
            String actorId = extractUserIdFromRequest(request);
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
     * Mark an EXISTING course as the public no-login demo (reuses its real
     * curriculum, images and practical scripts). Only one demo at a time.
     */
    @PutMapping("/courses/{courseId}/set-demo")
    public ResponseEntity<ApiResponse<Course>> setCourseAsDemo(
            @PathVariable String courseId,
            HttpServletRequest request) {
        try {
            assertModulePermission(request, AdminModule.COURSES, AdminPermissionAction.UPDATE);
            Course demo = courseService.setCourseAsDemo(courseId);
            return ResponseEntity.ok(new ApiResponse<>(200, demo));
        } catch (com.prwatech.common.exception.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(404, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
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
            assertModulePermission(httpRequest, AdminModule.ASSIGNMENTS, AdminPermissionAction.CREATE);
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
            if (isModuleForbidden(e)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, null));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(500, null));
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
            assertModulePermission(httpRequest, AdminModule.ASSIGNMENTS, AdminPermissionAction.DELETE);
            String unassignedBy = extractUserIdFromRequest(httpRequest);
            adminService.unassignCourse(body.getUserId(), body.getCourseId(), unassignedBy);
            return ResponseEntity.ok(new ApiResponse<>(200, null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, null));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, null));
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("Authorization")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse<>(401, null));
            }
            if (isModuleForbidden(e)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, null));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(500, null));
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
            assertModulePermission(request, AdminModule.DASHBOARD, AdminPermissionAction.READ);
            DashboardStatsDTO stats = adminService.getDashboardStatistics();
            return ResponseEntity.ok(new ApiResponse<>(200, stats));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, null));
        }
    }

    @ApiOperation(value = "LMS theme analytics", notes = "Classic vs Aurora theme switches and active preferences (Admin/Owner)")
    @GetMapping("/analytics/lms-themes")
    public ResponseEntity<ApiResponse<LmsThemeStatsDTO>> getLmsThemeStats(HttpServletRequest request) {
        try {
            assertModulePermission(request, AdminModule.ANALYTICS, AdminPermissionAction.READ);
            return ResponseEntity.ok(new ApiResponse<>(200, lmsThemeService.getStats()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
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
            assertModulePermission(httpRequest, AdminModule.FREEMIUM, AdminPermissionAction.UPDATE);
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
     * OWNER: seed balanced course progress for learner dashboard screenshots.
     * POST ?email=learner@example.com&amp;assignAll=true
     */
    @PostMapping("/users/seed-demo-dashboard")
    public ResponseEntity<ApiResponse<DemoDashboardSeedResultDTO>> seedDemoDashboard(
            @RequestParam String email,
            @RequestParam(defaultValue = "true") boolean assignAll,
            HttpServletRequest httpRequest) {
        try {
            String ownerId = extractUserIdFromRequest(httpRequest);
            adminService.requireOwner(ownerId);
            DemoDashboardSeedResultDTO result = demoDashboardSeedService.seedForEmail(email, ownerId, assignAll);
            adminAuditService.log(ownerId, AdminAuditService.USER_UPDATE, "USER", result.getUserId(),
                    "Seeded demo dashboard progress for " + email, null);
            return ResponseEntity.ok(new ApiResponse<>(200, result));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(404, null));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(400, null));
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("Owner access")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, null));
            }
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
            assertModulePermission(httpRequest, AdminModule.FREEMIUM, AdminPermissionAction.UPDATE);
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

    /** Adjusts the learner's AI wallet in USD — the dollar wallet is the only consumption limit. */
    @PostMapping("/users/{userId}/credits/adjust")
    public ResponseEntity<ApiResponse<WalletAdjustResultDTO>> adjustUserCredits(
            @PathVariable String userId,
            @RequestBody CreditAdjustRequestDTO body,
            HttpServletRequest httpRequest) {
        try {
            assertModulePermission(httpRequest, AdminModule.FREEMIUM, AdminPermissionAction.UPDATE);
            String adminId = extractUserIdFromRequest(httpRequest);
            WalletAdjustResultDTO result = freemiumService.adjustWalletBalance(userId, body, adminId);
            adminAuditService.log(adminId, AdminAuditService.CREDIT_ADJUST, "USER", userId,
                    "AI wallet adjust deltaUsd=" + result.getDeltaUsd()
                            + " walletUsd " + result.getWalletBeforeUsd() + " → " + result.getWalletAfterUsd()
                            + " (referralBonusUsd=" + result.getReferralBonusUsd()
                            + ", effectiveLimitUsd=" + result.getEffectiveLimitUsd() + ")"
                            + " reason=" + result.getReason(), null);
            return ResponseEntity.ok(new ApiResponse<>(200, result));
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
            assertModulePermission(request, AdminModule.UPGRADE_REQUESTS, AdminPermissionAction.READ);
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
            assertModulePermission(request, AdminModule.UPGRADE_REQUESTS, AdminPermissionAction.UPDATE);
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
     * OWNER: backfill legacy users (planTier null) to FREEMIUM with product defaults.
     * Default dryRun=true — pass dryRun=false to apply.
     */
    @PostMapping("/maintenance/backfill-legacy-to-freemium")
    public ResponseEntity<ApiResponse<LegacyFreemiumBackfillResultDTO>> backfillLegacyToFreemium(
            @RequestParam(defaultValue = "true") boolean dryRun,
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @RequestParam(defaultValue = "false") boolean allowMissingPhone,
            HttpServletRequest request) {
        try {
            String adminId = extractUserIdFromRequest(request);
            adminService.requireOwner(adminId);
            LegacyFreemiumBackfillResultDTO result = freemiumService.backfillLegacyUsersToFreemium(
                    dryRun, includeInactive, allowMissingPhone);
            adminAuditService.log(adminId, AdminAuditService.PLAN_UPDATE, "SYSTEM", "legacy-freemium-backfill",
                    (dryRun ? "Dry-run: " : "Applied: ")
                            + result.getMigrated() + " migrated, "
                            + result.getSkippedNoPhone() + " skipped (no phone), "
                            + result.getSkippedStaff() + " skipped (staff), "
                            + result.getSkippedInactive() + " skipped (inactive)",
                    null);
            return ResponseEntity.ok(new ApiResponse<>(200, result));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    /**
     * OWNER: reconcile LMS progress for every active enrollment (all learners × assigned courses).
     * Merges dashboard/legacy progress into profiling so module locks match completed lectures.
     * Default dryRun=true — pass dryRun=false to apply.
     */
    @PostMapping("/maintenance/reconcile-all-progress")
    public ResponseEntity<ApiResponse<BulkReconcileProgressResultDTO>> reconcileAllProgress(
            @RequestParam(defaultValue = "true") boolean dryRun,
            HttpServletRequest request) {
        try {
            String adminId = extractUserIdFromRequest(request);
            adminService.requireOwner(adminId);
            BulkReconcileProgressResultDTO result = progressReconciliationService.reconcileAllActiveEnrollments(dryRun);
            adminAuditService.log(adminId, AdminAuditService.USER_UPDATE, "SYSTEM", "progress-reconcile-all",
                    (dryRun ? "Dry-run: " : "Applied: ")
                            + result.getEnrollmentsProcessed() + " enrollments, "
                            + result.getUniqueUsers() + " users, "
                            + result.getTotalLecturesSynced() + " lectures synced, "
                            + result.getFailures() + " failures",
                    null);
            return ResponseEntity.ok(new ApiResponse<>(200, result));
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
            assertModulePermission(request, AdminModule.AUDIT_LOGS, AdminPermissionAction.READ);
            return ResponseEntity.ok(new ApiResponse<>(200,
                    adminAuditService.list(page, size, action, actorId)));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    /**
     * Monitor learner/guest AI chat exchanges (question, answer, course, lecture, time).
     */
    @GetMapping("/chat-interactions")
    public ResponseEntity<ApiResponse<Page<AdminChatInteractionDTO>>> listChatInteractions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String courseId,
            @RequestParam(required = false) String email,
            HttpServletRequest request) {
        try {
            assertModulePermission(request, AdminModule.CHAT_MONITOR, AdminPermissionAction.READ);
            adminAuditService.log(extractUserIdFromRequest(request), AdminAuditService.CHAT_MONITOR_VIEW,
                    "CHAT_INTERACTIONS", null,
                    "Viewed chat interactions (page=" + page + ", userId=" + userId
                            + ", courseId=" + courseId + ", email=" + email + ")", null);
            return ResponseEntity.ok(new ApiResponse<>(200,
                    userProfileService.listAdminChatInteractions(page, size, userId, courseId, email)));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    /**
     * Monitor AI Mentor doubts (question, latest AI answer, course, status, time).
     */
    @GetMapping("/ai-mentor-doubts")
    public ResponseEntity<ApiResponse<Page<AdminAiMentorDoubtDTO>>> listAiMentorDoubts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String courseId,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) com.prwatech.skillama.model.DoubtStatus status,
            HttpServletRequest request) {
        try {
            assertModulePermission(request, AdminModule.AI_MENTOR_DOUBTS, AdminPermissionAction.READ);
            adminAuditService.log(extractUserIdFromRequest(request), AdminAuditService.AI_MENTOR_DOUBTS_VIEW,
                    "AI_MENTOR_DOUBTS", null,
                    "Viewed AI Mentor doubts (page=" + page + ", userId=" + userId
                            + ", courseId=" + courseId + ", email=" + email + ")", null);
            return ResponseEntity.ok(new ApiResponse<>(200,
                    doubtService.listAdminDoubts(page, size, userId, courseId, email, status)));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    /**
     * Monitor AI Exam attempts (score, difficulty, exam type, course, time).
     */
    @GetMapping("/ai-exams")
    public ResponseEntity<ApiResponse<Page<AdminExamAttemptDTO>>> listAiExamAttempts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String courseId,
            @RequestParam(required = false) String email,
            HttpServletRequest request) {
        try {
            assertModulePermission(request, AdminModule.AI_EXAMS, AdminPermissionAction.READ);
            adminAuditService.log(extractUserIdFromRequest(request), AdminAuditService.AI_EXAM_VIEW,
                    "AI_EXAM_ATTEMPTS", null,
                    "Viewed AI Exam attempts (page=" + page + ", userId=" + userId
                            + ", courseId=" + courseId + ", email=" + email + ")", null);
            return ResponseEntity.ok(new ApiResponse<>(200,
                    examService.listAdminAttempts(page, size, userId, courseId, email)));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    /**
     * Monitor Module Quiz attempts across all learners (score, pass/fail, module, course, time).
     */
    @GetMapping("/module-quiz-attempts")
    public ResponseEntity<ApiResponse<Page<AdminModuleQuizAttemptDTO>>> listModuleQuizAttempts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String courseId,
            @RequestParam(required = false) String moduleName,
            @RequestParam(required = false) String email,
            HttpServletRequest request) {
        try {
            assertModulePermission(request, AdminModule.MODULE_QUIZ_MONITOR, AdminPermissionAction.READ);
            adminAuditService.log(extractUserIdFromRequest(request), AdminAuditService.MODULE_QUIZ_MONITOR_VIEW,
                    "MODULE_QUIZ_ATTEMPTS", null,
                    "Viewed Module Quiz attempts (page=" + page + ", userId=" + userId
                            + ", courseId=" + courseId + ", moduleName=" + moduleName + ", email=" + email + ")", null);
            return ResponseEntity.ok(new ApiResponse<>(200,
                    moduleQuizService.listAdminAttempts(page, size, userId, courseId, moduleName, email)));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    /**
     * Monitor "AI Recommended Test" suggestions across all learners (same permission as AI Exam
     * attempts — it's a sub-view of the same feature, not a separate module).
     */
    @GetMapping("/ai-exam-recommendations")
    public ResponseEntity<ApiResponse<Page<AdminExamRecommendationDTO>>> listAiExamRecommendations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String courseId,
            @RequestParam(required = false) String email,
            HttpServletRequest request) {
        try {
            assertModulePermission(request, AdminModule.AI_EXAMS, AdminPermissionAction.READ);
            adminAuditService.log(extractUserIdFromRequest(request), AdminAuditService.AI_EXAM_RECOMMENDATIONS_VIEW,
                    "AI_EXAM_RECOMMENDATIONS", null,
                    "Viewed AI Exam recommendations (page=" + page + ", userId=" + userId
                            + ", courseId=" + courseId + ", email=" + email + ")", null);
            return ResponseEntity.ok(new ApiResponse<>(200,
                    examService.listAdminRecommendations(page, size, userId, courseId, email)));
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
            assertModulePermission(request, AdminModule.SETTINGS_DEMO_VIDEO, AdminPermissionAction.READ);
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
            assertModulePermission(request, AdminModule.SETTINGS_DEMO_VIDEO, AdminPermissionAction.CREATE);
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
            assertModulePermission(request, AdminModule.SETTINGS_DEMO_VIDEO, AdminPermissionAction.UPDATE);
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
            assertModulePermission(request, AdminModule.SETTINGS_DEMO_VIDEO, AdminPermissionAction.UPDATE);
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
            assertModulePermission(request, AdminModule.SETTINGS_DEMO_VIDEO, AdminPermissionAction.DELETE);
            String adminUserId = extractUserIdFromRequest(request);
            platformDemoVideoService.remove(adminUserId);
            return ResponseEntity.ok(new ApiResponse<>(200, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    @GetMapping("/platform/upgrade-contact")
    public ResponseEntity<ApiResponse<UpgradeContactDTO>> getUpgradeContactAdminConfig(HttpServletRequest request) {
        try {
            extractUserIdFromRequest(request);
            return ResponseEntity.ok(new ApiResponse<>(200, platformConfigService.getUpgradeContact()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    @PutMapping("/platform/upgrade-contact")
    public ResponseEntity<ApiResponse<UpgradeContactDTO>> updateUpgradeContactConfig(
            @RequestBody UpdateUpgradeContactDTO body,
            HttpServletRequest request) {
        try {
            String userId = extractUserIdFromRequest(request);
            adminService.requireOwner(userId);
            return ResponseEntity.ok(
                    new ApiResponse<>(200, platformConfigService.updateUpgradeContact(body, userId)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, null));
        } catch (RuntimeException e) {
            if (e.getMessage() != null
                    && (e.getMessage().contains("Only OWNER") || e.getMessage().contains("Owner access"))) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, null));
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    @GetMapping("/platform/ai-dev-mode")
    public ResponseEntity<ApiResponse<AiSettingsDTO>> getAiDevModeAdminConfig(HttpServletRequest request) {
        try {
            String userId = extractUserIdFromRequest(request);
            adminService.requireOwner(userId);
            return ResponseEntity.ok(new ApiResponse<>(200, platformAiSettingsService.getPublicSettings()));
        } catch (RuntimeException e) {
            if (e.getMessage() != null
                    && (e.getMessage().contains("Only OWNER") || e.getMessage().contains("Owner access"))) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, null));
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    @PutMapping("/platform/ai-dev-mode")
    public ResponseEntity<ApiResponse<AiSettingsDTO>> updateAiDevModeConfig(
            @RequestBody UpdateAiDevModeDTO body,
            HttpServletRequest request) {
        try {
            String userId = extractUserIdFromRequest(request);
            adminService.requireOwner(userId);
            return ResponseEntity.ok(
                    new ApiResponse<>(200, platformAiSettingsService.updateDevMode(body, userId)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, null));
        } catch (RuntimeException e) {
            if (e.getMessage() != null
                    && (e.getMessage().contains("Only OWNER") || e.getMessage().contains("Owner access"))) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, null));
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    @GetMapping("/platform/referral-share")
    public ResponseEntity<ApiResponse<ReferralShareConfigDTO>> getReferralShareAdminConfig(HttpServletRequest request) {
        try {
            assertModulePermission(request, AdminModule.SETTINGS_REFERRAL, AdminPermissionAction.READ);
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
            assertModulePermission(request, AdminModule.SETTINGS_REFERRAL, AdminPermissionAction.UPDATE);
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
            assertModulePermission(request, AdminModule.SETTINGS_NOTIFICATIONS, AdminPermissionAction.READ);
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
            assertModulePermission(request, AdminModule.SETTINGS_NOTIFICATIONS, AdminPermissionAction.UPDATE);
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
            assertModulePermission(request, AdminModule.FEEDBACK, AdminPermissionAction.READ);
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
            assertModulePermission(request, AdminModule.FEEDBACK, AdminPermissionAction.UPDATE);
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
     * List reported issues / support tickets for admin triage.
     */
    @GetMapping("/issues")
    public ResponseEntity<ApiResponse<Page<IssueReport>>> listIssues(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            HttpServletRequest request) {
        try {
            assertModulePermission(request, AdminModule.SUPPORT, AdminPermissionAction.READ);
            Page<IssueReport> issues = issueReportService.getIssuesForAdmin(status, page, size);
            return ResponseEntity.ok(new ApiResponse<>(200, issues));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    /**
     * Update the workflow status of a reported issue (OPEN / IN_PROGRESS / RESOLVED).
     */
    @PutMapping("/issues/{issueId}/status")
    public ResponseEntity<ApiResponse<IssueReport>> updateIssueStatus(
            @PathVariable String issueId,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        try {
            assertModulePermission(request, AdminModule.SUPPORT, AdminPermissionAction.UPDATE);
            IssueReport updated = issueReportService.updateStatus(issueId, body.get("status"));
            String adminUserId = extractUserIdFromRequest(request);
            adminAuditService.log(adminUserId, "ISSUE_STATUS_UPDATE", "ISSUE", issueId,
                    "Set issue status to " + updated.getStatus(), null);
            return ResponseEntity.ok(new ApiResponse<>(200, updated));
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

    @GetMapping("/users/{userId}/module-quiz/attempts")
    public ResponseEntity<ApiResponse<List<ModuleQuizAttemptDetailDTO>>> getUserModuleQuizAttempts(
            @PathVariable String userId,
            @RequestParam(required = false) String courseId,
            HttpServletRequest request) {
        try {
            extractUserIdFromRequest(request);
            return ResponseEntity.ok(new ApiResponse<>(
                    200, adminService.getUserModuleQuizAttempts(userId, courseId)));
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

    @PostMapping("/users/{userId}/progress/reconcile-all")
    public ResponseEntity<ApiResponse<BulkReconcileProgressResultDTO>> reconcileUserAllCoursesProgress(
            @PathVariable String userId,
            @RequestParam(defaultValue = "false") boolean dryRun,
            HttpServletRequest request) {
        try {
            String adminId = extractUserIdFromRequest(request);
            assertModulePermission(request, AdminModule.USERS, AdminPermissionAction.UPDATE);
            BulkReconcileProgressResultDTO result = progressReconciliationService.reconcileAllCoursesForUser(userId, dryRun);
            adminAuditService.log(adminId, AdminAuditService.USER_UPDATE, userId, "progress-reconcile-user",
                    (dryRun ? "Dry-run: " : "Applied: ")
                            + result.getEnrollmentsProcessed() + " enrollments, "
                            + result.getTotalLecturesSynced() + " lectures synced, "
                            + result.getFailures() + " failures",
                    null);
            return ResponseEntity.ok(new ApiResponse<>(200, result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, null));
        } catch (RuntimeException e) {
            if (isModuleForbidden(e)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, null));
            }
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(500, null));
        }
    }

    @PostMapping("/users/{userId}/progress/reconcile")
    public ResponseEntity<ApiResponse<ReconcileProgressResultDTO>> reconcileUserProgress(
            @PathVariable String userId,
            @RequestBody ReconcileProgressRequestDTO body,
            HttpServletRequest request) {
        try {
            assertModulePermission(request, AdminModule.USERS, AdminPermissionAction.UPDATE);
            ReconcileProgressResultDTO result = progressReconciliationService.reconcileForUserAsAdmin(userId, body);
            return ResponseEntity.ok(new ApiResponse<>(200, result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, null));
        } catch (RuntimeException e) {
            if (isModuleForbidden(e)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, null));
            }
            if (e.getMessage() != null && e.getMessage().contains("Authorization")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
            }
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(500, null));
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
    private void assertModulePermission(
            HttpServletRequest request, AdminModule module, AdminPermissionAction action) {
        adminPermissionService.requirePermission(extractUserIdFromRequest(request), module, action);
    }

    private boolean isOwnerForbidden(RuntimeException e) {
        return e.getMessage() != null
                && (e.getMessage().contains("Owner access") || e.getMessage().contains("Only OWNER"));
    }

    private boolean isModuleForbidden(RuntimeException e) {
        return e.getMessage() != null && e.getMessage().contains("Insufficient permission");
    }

    private String extractUserIdFromRequest(HttpServletRequest request) {
        return skillamaAuthSupport.resolveUserIdFromRequest(request);
    }
}


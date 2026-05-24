package com.prwatech.skillama.service;

import com.prwatech.common.configuration.PasswordEncode;
import com.prwatech.skillama.dto.*;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.model.UserCourseEnrollment;
import com.prwatech.skillama.model.QueryActivityLog;
import com.prwatech.skillama.model.UserCourseProgress;
import com.prwatech.skillama.model.UserProfile;
import com.prwatech.skillama.model.Review;
import com.prwatech.skillama.repository.CourseRepository;
import com.prwatech.skillama.repository.IssueReportRepository;
import com.prwatech.skillama.repository.QueryActivityLogRepository;
import com.prwatech.skillama.repository.ReviewRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.repository.UserCourseEnrollmentRepository;
import com.prwatech.skillama.repository.UserCourseProgressRepository;
import com.prwatech.skillama.repository.DeletedSkillamaUserRepository;
import com.prwatech.skillama.model.DeletedSkillamaUser;
import com.prwatech.skillama.repository.UserLoginEventRepository;
import com.prwatech.skillama.repository.UserProfileRepository;
import com.prwatech.skillama.util.IndiaTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {
    
    private final SkillamaUserRepository userRepository;
    private final CourseRepository courseRepository;
    private final UserCourseEnrollmentRepository enrollmentRepository;
    private final UserCourseProgressRepository progressRepository;
    private final UserProfileRepository userProfileRepository;
    private final QueryActivityLogRepository queryActivityLogRepository;
    private final UserLoginEventRepository userLoginEventRepository;
    private final ReviewRepository reviewRepository;
    private final IssueReportRepository issueReportRepository;
    private final UserCourseAccessService userCourseAccessService;
    private final CourseAssignmentNotificationService courseAssignmentNotificationService;
    private final PasswordEncode passwordEncode;
    private final AdminAuditService adminAuditService;
    private final DeletedSkillamaUserRepository deletedSkillamaUserRepository;

    public User requireAdminOrOwner(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getRole() != User.UserRole.ADMIN && user.getRole() != User.UserRole.OWNER) {
            throw new RuntimeException("Admin access required");
        }
        return user;
    }

    public User requireOwner(String userId) {
        User user = requireAdminOrOwner(userId);
        if (user.getRole() != User.UserRole.OWNER) {
            throw new RuntimeException("Owner access required");
        }
        return user;
    }

    /** Course assignment is for learners only (USER role), not ADMIN/OWNER. */
    private static void assertCourseAssignableLearner(User user) {
        if (user.getRole() == User.UserRole.ADMIN || user.getRole() == User.UserRole.OWNER) {
            throw new IllegalArgumentException(
                    "Course assignment applies to learners only. Admin and owner accounts cannot be assigned courses.");
        }
    }

    private static boolean isCourseAssignableLearner(User user) {
        if (user == null) {
            return false;
        }
        return user.getRole() != User.UserRole.ADMIN && user.getRole() != User.UserRole.OWNER;
    }
    
    public AdminAccessDTO checkAdminAccess(String userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        if (user.getRole() != User.UserRole.ADMIN && user.getRole() != User.UserRole.OWNER) {
            return AdminAccessDTO.builder()
                .hasAccess(false)
                .role(user.getRole() != null ? user.getRole().name() : "USER")
                .permissions(new ArrayList<>())
                .build();
        }
        
        List<String> permissions = getPermissions(user.getRole());
        
        return AdminAccessDTO.builder()
            .hasAccess(true)
            .role(user.getRole().name())
            .permissions(permissions)
            .build();
    }
    
    public Page<UserDTO> getUsers(
            int page,
            int size,
            String search,
            User.UserRole role,
            Boolean active,
            String phone,
            User.PlanTier planTier,
            LocalDateTime fromDate,
            LocalDateTime toDate) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<User> users = userRepository.findUsersWithFilters(
                search, role, active, phone, planTier, fromDate, toDate, pageable);

        return users.map(this::convertToUserDTO);
    }
    
    @Transactional
    public UserDTO createUser(CreateUserRequest request, String createdBy) {
        // Check if creator has permission
        User creator = userRepository.findById(createdBy)
            .orElseThrow(() -> new ResourceNotFoundException("Creator not found"));
        
        // Only OWNER can create ADMIN/OWNER
        if ((request.getRole() == User.UserRole.ADMIN || request.getRole() == User.UserRole.OWNER) 
            && creator.getRole() != User.UserRole.OWNER) {
            throw new RuntimeException("Only OWNER can create ADMIN or OWNER users");
        }
        
        // Check if email exists
        if (userRepository.findByEmail(request.getEmail().trim()).isPresent()) {
            throw new IllegalStateException("Email is already registered");
        }
        
        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncode.getEncryptedPassword(request.getPassword()));
        user.setRole(request.getRole() != null ? request.getRole() : User.UserRole.USER);
        user.setActive(request.getActive() != null ? request.getActive() : true);
        user.setGender(request.getGender());
        user.setCreatedAt(IndiaTime.now());
        user.setUpdatedAt(IndiaTime.now());
        user.setCreatedBy(createdBy);
        user.setUpdatedBy(createdBy);
        
        user = userRepository.save(user);
        adminAuditService.log(createdBy, AdminAuditService.USER_CREATE, "USER", user.getId(),
                "Created user " + user.getEmail(), null);
        return convertToUserDTO(user);
    }
    
    @Transactional
    public UserDTO createAdminUser(CreateUserRequest request, String createdBy) {
        // Only OWNER can create admin
        User creator = userRepository.findById(createdBy)
            .orElseThrow(() -> new ResourceNotFoundException("Creator not found"));
        
        if (creator.getRole() != User.UserRole.OWNER) {
            throw new RuntimeException("Only OWNER can create admin users");
        }
        
        return createUser(request, createdBy);
    }
    
    @Transactional
    public UserDTO updateUser(String userId, UpdateUserRequest request, String updatedBy) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        User updater = userRepository.findById(updatedBy)
            .orElseThrow(() -> new ResourceNotFoundException("Updater not found"));
        
        // Only OWNER can grant ADMIN/OWNER or remove ADMIN role
        if (request.getRole() != null) {
            if ((request.getRole() == User.UserRole.ADMIN || request.getRole() == User.UserRole.OWNER)
                    && updater.getRole() != User.UserRole.OWNER) {
                throw new RuntimeException("Only OWNER can change role to ADMIN or OWNER");
            }
            if (user.getRole() == User.UserRole.ADMIN
                    && request.getRole() == User.UserRole.USER
                    && updater.getRole() != User.UserRole.OWNER) {
                throw new RuntimeException("Only OWNER can remove admin role");
            }
        }

        // Cannot change OWNER role
        if (user.getRole() == User.UserRole.OWNER && request.getRole() != User.UserRole.OWNER) {
            throw new RuntimeException("Cannot change OWNER role");
        }
        
        if (request.getName() != null) user.setName(request.getName());
        if (request.getEmail() != null) {
            // Check if email is already taken by another user
            userRepository.findByEmail(request.getEmail())
                .ifPresent(existing -> {
                    if (!existing.getId().equals(userId)) {
                        throw new RuntimeException("Email already exists");
                    }
                });
            user.setEmail(request.getEmail());
        }
        if (request.getRole() != null) user.setRole(request.getRole());
        if (request.getActive() != null) {
            if (!request.getActive()) {
                assertCanChangeUserActiveStatus(updater, user);
            }
            user.setActive(request.getActive());
        }
        if (request.getGender() != null) user.setGender(request.getGender());
        
        user.setUpdatedAt(IndiaTime.now());
        user.setUpdatedBy(updatedBy);
        
        user = userRepository.save(user);
        adminAuditService.log(updatedBy, AdminAuditService.USER_UPDATE, "USER", user.getId(),
                "Updated user " + user.getEmail(), null);
        return convertToUserDTO(user);
    }
    
    /**
     * Update user role by email. Only OWNER can promote users to ADMIN/OWNER.
     * 
     * @param email The email of the user to update
     * @param newRole The new role (ADMIN, OWNER, or USER)
     * @param updatedBy The ID of the user making the update
     * @return Updated user DTO
     */
    @Transactional
    public UserDTO updateUserRoleByEmail(String email, User.UserRole newRole, String updatedBy) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
        
        User updater = userRepository.findById(updatedBy)
            .orElseThrow(() -> new ResourceNotFoundException("Updater not found"));
        
        // Only OWNER can change roles to ADMIN/OWNER
        if ((newRole == User.UserRole.ADMIN || newRole == User.UserRole.OWNER)
            && updater.getRole() != User.UserRole.OWNER) {
            throw new RuntimeException("Only OWNER can change role to ADMIN or OWNER");
        }
        
        // Cannot change OWNER role
        if (user.getRole() == User.UserRole.OWNER && newRole != User.UserRole.OWNER) {
            throw new RuntimeException("Cannot change OWNER role");
        }
        
        if (newRole == User.UserRole.USER
                && user.getRole() == User.UserRole.ADMIN
                && updater.getRole() != User.UserRole.OWNER) {
            throw new RuntimeException("Only OWNER can remove admin role");
        }

        user.setRole(newRole);
        user.setUpdatedAt(IndiaTime.now());
        user.setUpdatedBy(updatedBy);
        
        user = userRepository.save(user);
        adminAuditService.log(updatedBy, AdminAuditService.USER_UPDATE, "USER", user.getId(),
                "Changed role to " + newRole + " for " + user.getEmail(), null);
        return convertToUserDTO(user);
    }

    @Transactional
    public UserDTO promoteUserToAdmin(String userId, String updatedBy) {
        requireOwner(updatedBy);
        UpdateUserRequest request = new UpdateUserRequest();
        request.setRole(User.UserRole.ADMIN);
        return updateUser(userId, request, updatedBy);
    }

    @Transactional
    public UserDTO demoteAdminToUser(String userId, String updatedBy) {
        requireOwner(updatedBy);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getRole() == User.UserRole.OWNER) {
            throw new RuntimeException("Cannot change OWNER role");
        }
        if (user.getRole() != User.UserRole.ADMIN) {
            throw new IllegalArgumentException("User is not an admin");
        }
        UpdateUserRequest request = new UpdateUserRequest();
        request.setRole(User.UserRole.USER);
        return updateUser(userId, request, updatedBy);
    }
    
    @Transactional
    public void deleteUser(String userId, String deletedBy, boolean hardDelete, String reason) {
        User deleter = requireAdminOrOwner(deletedBy);
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        if (user.getRole() == User.UserRole.OWNER) {
            throw new RuntimeException("Cannot delete OWNER user");
        }
        if (user.getRole() == User.UserRole.ADMIN && deleter.getRole() != User.UserRole.OWNER) {
            throw new RuntimeException("Only OWNER can delete ADMIN users");
        }
        if (deleter.getId().equals(userId)) {
            throw new RuntimeException("Cannot delete your own account");
        }

        if (hardDelete) {
            if (deleter.getRole() != User.UserRole.OWNER) {
                throw new RuntimeException("Only OWNER can permanently delete users");
            }
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("reason is required for permanent delete");
            }
            archiveAndHardDeleteUser(user, deleter, reason.trim());
            adminAuditService.log(deletedBy, AdminAuditService.USER_HARD_DELETE, "USER", userId,
                    "Permanently deleted user " + user.getEmail() + ": " + reason, null);
            return;
        }
        
        user.setActive(false);
        user.setUpdatedAt(IndiaTime.now());
        user.setUpdatedBy(deletedBy);
        userRepository.save(user);
        adminAuditService.log(deletedBy, AdminAuditService.USER_DELETE, "USER", userId,
                "Deactivated user " + user.getEmail(), null);
    }

    private void archiveAndHardDeleteUser(User user, User deleter, String reason) {
        DeletedSkillamaUser archive = DeletedSkillamaUser.builder()
                .originalUserId(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole())
                .planTier(user.getPlanTier())
                .deletedByAdminId(deleter.getId())
                .deletedByAdminEmail(deleter.getEmail())
                .reason(reason)
                .deletedAt(IndiaTime.now())
                .build();
        deletedSkillamaUserRepository.save(archive);
        userRepository.delete(user);
    }

    @Transactional
    public User activateUserByAdmin(String email, String actorId) {
        User actor = requireAdminOrOwner(actorId);
        User target = userRepository.findByEmail(email.trim())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        assertCanChangeUserActiveStatus(actor, target);
        target.setActive(true);
        target.setActivationKey(null);
        target.setUpdatedAt(IndiaTime.now());
        target.setUpdatedBy(actorId);
        target = userRepository.save(target);
        adminAuditService.log(actorId, AdminAuditService.USER_UPDATE, "USER", target.getId(),
                "Activated user " + target.getEmail(), null);
        return target;
    }

    @Transactional
    public User deactivateUserByAdmin(String email, String actorId) {
        User actor = requireAdminOrOwner(actorId);
        User target = userRepository.findByEmail(email.trim())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        assertCanChangeUserActiveStatus(actor, target);
        target.setActive(false);
        target.setUpdatedAt(IndiaTime.now());
        target.setUpdatedBy(actorId);
        target = userRepository.save(target);
        adminAuditService.log(actorId, AdminAuditService.USER_UPDATE, "USER", target.getId(),
                "Deactivated user " + target.getEmail(), null);
        return target;
    }

    private void assertCanChangeUserActiveStatus(User actor, User target) {
        if (target.getRole() == User.UserRole.OWNER) {
            throw new RuntimeException("Cannot change status of OWNER user");
        }
        if (target.getRole() == User.UserRole.ADMIN && actor.getRole() != User.UserRole.OWNER) {
            throw new RuntimeException("Only OWNER can change status of ADMIN users");
        }
        if (actor.getId().equals(target.getId())) {
            throw new RuntimeException("Cannot change status of your own account");
        }
    }

    @Transactional
    public void resetAdminPassword(String ownerId, String targetUserId, String newPassword) {
        User owner = requireOwner(ownerId);
        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (target.getRole() != User.UserRole.ADMIN) {
            throw new RuntimeException("Password reset is only allowed for ADMIN accounts");
        }
        target.setPassword(passwordEncode.getEncryptedPassword(newPassword));
        target.setUpdatedAt(IndiaTime.now());
        target.setUpdatedBy(ownerId);
        userRepository.save(target);
        adminAuditService.log(ownerId, AdminAuditService.ADMIN_PASSWORD_RESET, "USER", targetUserId,
                "OWNER reset password for admin " + target.getEmail(), null);
    }
    
    @Transactional
    public AssignmentResponseDTO assignCourses(String userId, List<String> courseIds, String assignedBy) {
        requireAdminOrOwner(assignedBy);
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        assertCourseAssignableLearner(user);
        
        List<UserCourseEnrollment> enrollments = new ArrayList<>();
        LocalDateTime now = IndiaTime.now();
        
        for (String courseId : courseIds) {
            Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found: " + courseId));
            if (course.getDeletedAt() != null) {
                throw new IllegalStateException("Cannot assign archived course: " + courseId);
            }
            
            java.util.Optional<UserCourseEnrollment> existingEnrollment =
                    enrollmentRepository.findByUserIdAndCourseId(userId, courseId);
            if (existingEnrollment.isPresent()) {
                UserCourseEnrollment existing = existingEnrollment.get();
                if (existing.getStatus() == UserCourseEnrollment.EnrollmentStatus.ACTIVE) {
                    continue;
                }
                existing.setStatus(UserCourseEnrollment.EnrollmentStatus.ACTIVE);
                existing.setEnrollmentType(UserCourseEnrollment.EnrollmentType.ASSIGNED);
                existing.setEnrolledAt(now);
                enrollments.add(existing);
            } else {
                UserCourseEnrollment enrollment = new UserCourseEnrollment();
                enrollment.setUserId(userId);
                enrollment.setCourseId(courseId);
                enrollment.setEnrollmentType(UserCourseEnrollment.EnrollmentType.ASSIGNED);
                enrollment.setEnrolledAt(now);
                enrollment.setStatus(UserCourseEnrollment.EnrollmentStatus.ACTIVE);
                enrollments.add(enrollment);
            }
        }

        if (!enrollments.isEmpty()) {
            enrollmentRepository.saveAll(enrollments);
        }
        
        // Initialize progress for new enrollments
        for (UserCourseEnrollment enrollment : enrollments) {
            initializeCourseProgress(enrollment.getUserId(), enrollment.getCourseId());
        }

        if (!enrollments.isEmpty()) {
            List<String> assignedIds = enrollments.stream()
                    .map(UserCourseEnrollment::getCourseId)
                    .collect(Collectors.toList());
            courseAssignmentNotificationService.notifyCoursesAssigned(user, assignedIds);
            adminAuditService.log(assignedBy, AdminAuditService.ASSIGN_COURSE, "USER", userId,
                    "Assigned " + assignedIds.size() + " course(s) to " + user.getEmail(),
                    String.join(",", assignedIds));
        }

        return AssignmentResponseDTO.builder()
            .userId(userId)
            .assignedCourses(enrollments.size())
            .enrollments(enrollments.stream()
                .map(e -> {
                    Course course = courseRepository.findById(e.getCourseId()).orElse(null);
                    return AssignmentResponseDTO.EnrollmentDTO.builder()
                        .courseId(e.getCourseId())
                        .courseName(course != null ? course.getName() : "Unknown")
                        .enrolledAt(e.getEnrolledAt())
                        .build();
                })
                .collect(Collectors.toList()))
            .build();
    }
    
    @Transactional
    public void unassignCourse(String userId, String courseId, String unassignedBy) {
        requireAdminOrOwner(unassignedBy);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        UserCourseEnrollment enrollment = enrollmentRepository
            .findByUserIdAndCourseId(userId, courseId)
            .orElseThrow(() -> new ResourceNotFoundException("Enrollment not found"));
        
        enrollment.setStatus(UserCourseEnrollment.EnrollmentStatus.INACTIVE);
        enrollmentRepository.save(enrollment);
        courseAssignmentNotificationService.notifyCourseUnassigned(user, courseId);
        adminAuditService.log(unassignedBy, AdminAuditService.UNASSIGN_COURSE, "USER", userId,
                "Unassigned course " + courseId + " from " + user.getEmail(), courseId);
    }
    
    public UserAssignmentsDTO getUserAssignments(String userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        List<UserCourseEnrollment> enrollments = enrollmentRepository
            .findByUserIdAndStatus(userId, UserCourseEnrollment.EnrollmentStatus.ACTIVE);
        
        List<UserAssignmentsDTO.CourseAssignmentDTO> courses = enrollments.stream()
            .map(enrollment -> {
                Course course = courseRepository.findById(enrollment.getCourseId())
                    .orElse(null);
                
                UserCourseProgress progress = progressRepository
                    .findByUserIdAndCourseId(userId, enrollment.getCourseId())
                    .orElse(null);
                
                UserAssignmentsDTO.CourseAssignmentDTO dto = new UserAssignmentsDTO.CourseAssignmentDTO();
                dto.setCourseId(enrollment.getCourseId());
                dto.setCourseName(course != null ? course.getName() : "Unknown");
                dto.setEnrolledAt(enrollment.getEnrolledAt());
                dto.setProgress(progress != null ? progress.getProgress() : 0);
                
                return dto;
            })
            .collect(Collectors.toList());
        
        UserAssignmentsDTO result = new UserAssignmentsDTO();
        result.setUserId(userId);
        result.setUserName(user.getName());
        result.setCourses(courses);
        
        return result;
    }
    
    public CourseAssignmentsDTO getCourseAssignments(String courseId) {
        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new ResourceNotFoundException("Course not found"));
        
        List<UserCourseEnrollment> enrollments = enrollmentRepository
            .findAll()
            .stream()
            .filter(e -> e.getCourseId().equals(courseId) 
                && e.getStatus() == UserCourseEnrollment.EnrollmentStatus.ACTIVE)
            .collect(Collectors.toList());
        
        List<CourseAssignmentsDTO.UserAssignmentDTO> users = enrollments.stream()
            .map(enrollment -> {
                User user = userRepository.findById(enrollment.getUserId())
                    .orElse(null);
                if (!isCourseAssignableLearner(user)) {
                    return null;
                }

                UserCourseProgress progress = progressRepository
                    .findByUserIdAndCourseId(enrollment.getUserId(), courseId)
                    .orElse(null);
                
                CourseAssignmentsDTO.UserAssignmentDTO dto = new CourseAssignmentsDTO.UserAssignmentDTO();
                dto.setUserId(enrollment.getUserId());
                dto.setUserName(user.getName());
                dto.setUserEmail(user.getEmail());
                dto.setEnrolledAt(enrollment.getEnrolledAt());
                dto.setProgress(progress != null ? progress.getProgress() : 0);
                
                return dto;
            })
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toList());
        
        CourseAssignmentsDTO result = new CourseAssignmentsDTO();
        result.setCourseId(courseId);
        result.setCourseName(course.getName());
        result.setUsers(users);
        result.setTotalEnrollments(users.size());
        
        return result;
    }
    
    public DashboardStatsDTO getDashboardStatistics() {
        List<User> allUsers = userRepository.findAll();
        long totalUsers = allUsers.stream()
                .filter(u -> u.getEffectiveRole() == User.UserRole.USER)
                .count();
        long activeUsers = allUsers.stream()
                .filter(u -> u.getEffectiveRole() == User.UserRole.USER && u.isActive())
                .count();
        long inactiveUsers = totalUsers - activeUsers;

        List<Course> allCourses = courseRepository.findAll();
        long totalCourses = allCourses.size();
        long activeCourses = allCourses.stream()
                .filter(c -> c.getDeletedAt() == null)
                .count();

        long totalEnrollments = enrollmentRepository.count();
        
        List<UserCourseProgress> allProgress = progressRepository.findAll();
        double averageProgress = normalizeAverageProgressPercent(allProgress);
        
        // Recent users (last 7 days) - simplified
        int recentUsers = (int) userRepository.findAll().stream()
            .filter(u -> u.getCreatedAt() != null 
                && u.getCreatedAt().isAfter(IndiaTime.now().minusDays(7)))
            .count();
        
        // Recent courses (last 7 days) - simplified
        int recentCourses = (int) courseRepository.findAll().stream()
            .filter(c -> c.getCreatedAt() != null 
                && c.getCreatedAt().isAfter(IndiaTime.now().minusDays(7)))
            .count();
        
        DashboardStatsDTO stats = new DashboardStatsDTO();
        stats.setTotalUsers(totalUsers);
        stats.setActiveUsers(activeUsers);
        stats.setInactiveUsers(inactiveUsers);
        stats.setTotalCourses(totalCourses);
        stats.setActiveCourses(activeCourses);
        stats.setTotalEnrollments(totalEnrollments);
        stats.setAverageProgress(averageProgress);
        stats.setRecentUsers(recentUsers);
        stats.setRecentCourses(recentCourses);
        stats.setTopCourses(buildTopCourseStats());
        stats.setRecentLogins(buildRecentLoginStats(15));

        return stats;
    }

    /** Average stored progress (0-100). No scaling; values under 1% stay as-is (e.g. 0.05). */
    private double normalizeAverageProgressPercent(List<UserCourseProgress> allProgress) {
        if (allProgress.isEmpty()) {
            return 0.0;
        }
        double avg = allProgress.stream()
                .mapToInt(p -> p.getProgress() != null ? p.getProgress() : 0)
                .average()
                .orElse(0.0);
        return Math.round(avg * 100.0) / 100.0;
    }

    private List<DashboardStatsDTO.TopCourseStatDTO> buildTopCourseStats() {
        return courseRepository.findAll().stream()
                .map(course -> {
                    List<UserCourseProgress> progressList = progressRepository.findAll().stream()
                            .filter(p -> course.getId().equals(p.getCourseId()))
                            .collect(Collectors.toList());
                    long enrollments = enrollmentRepository.findAll().stream()
                            .filter(e -> course.getId().equals(e.getCourseId())
                                    && e.getStatus() == UserCourseEnrollment.EnrollmentStatus.ACTIVE)
                            .count();
                    double avg = normalizeAverageProgressPercent(progressList);
                    return DashboardStatsDTO.TopCourseStatDTO.builder()
                            .courseId(course.getId())
                            .courseName(course.getName())
                            .enrollmentCount(enrollments)
                            .averageProgress(avg)
                            .build();
                })
                .sorted((a, b) -> Long.compare(b.getEnrollmentCount(), a.getEnrollmentCount()))
                .limit(8)
                .collect(Collectors.toList());
    }

    private List<DashboardStatsDTO.RecentLoginStatDTO> buildRecentLoginStats(int limit) {
        return userLoginEventRepository.findAll().stream()
                .filter(e -> e.getLoggedInAt() != null)
                .sorted((a, b) -> b.getLoggedInAt().compareTo(a.getLoggedInAt()))
                .limit(limit)
                .map(event -> {
                    User user = userRepository.findById(event.getUserId()).orElse(null);
                    return DashboardStatsDTO.RecentLoginStatDTO.builder()
                            .userId(event.getUserId())
                            .userName(user != null ? user.getName() : "Unknown")
                            .userEmail(user != null ? user.getEmail() : "")
                            .loggedInAt(event.getLoggedInAt())
                            .build();
                })
                .collect(Collectors.toList());
    }
    
    public CourseAnalyticsDTO getCourseAnalytics(String courseId) {
        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new ResourceNotFoundException("Course not found"));
        
        List<UserCourseEnrollment> allEnrollments = enrollmentRepository.findAll()
            .stream()
            .filter(e -> e.getCourseId().equals(courseId))
            .collect(Collectors.toList());
        
        long totalEnrollments = allEnrollments.size();
        long activeEnrollments = allEnrollments.stream()
            .filter(e -> e.getStatus() == UserCourseEnrollment.EnrollmentStatus.ACTIVE)
            .count();
        
        List<UserCourseProgress> progressList = progressRepository.findAll()
            .stream()
            .filter(p -> p.getCourseId().equals(courseId))
            .collect(Collectors.toList());
        
        long completedEnrollments = progressList.stream()
            .filter(p -> p.getProgress() != null && p.getProgress() == 100)
            .count();
        
        double averageProgress = progressList.isEmpty() ? 0.0 :
            progressList.stream()
                .mapToInt(p -> p.getProgress() != null ? p.getProgress() : 0)
                .average()
                .orElse(0.0);
        
        double completionRate = totalEnrollments > 0 
            ? (completedEnrollments * 100.0) / totalEnrollments 
            : 0.0;
        
        CourseAnalyticsDTO analytics = new CourseAnalyticsDTO();
        analytics.setCourseId(courseId);
        analytics.setCourseName(course.getName());
        analytics.setTotalEnrollments(totalEnrollments);
        analytics.setActiveEnrollments(activeEnrollments);
        analytics.setCompletedEnrollments(completedEnrollments);
        analytics.setAverageProgress(averageProgress);
        analytics.setCompletionRate(completionRate);
        
        return analytics;
    }
    
    public UserAdminProfileDTO getUserAdminProfile(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        UserProfile profile = userProfileRepository.findByUserId(userId).orElse(null);
        int completedCount = profile != null && profile.getCompletedLectures() != null
                ? profile.getCompletedLectures().size() : 0;
        int questionsAsked = profile != null && profile.getTotalQuestionsAsked() != null
                ? profile.getTotalQuestionsAsked() : 0;

        int loginCount = user.getLoginCount() != null ? user.getLoginCount() : 0;
        long eventLoginCount = userLoginEventRepository.countByUserId(userId);
        if (eventLoginCount > loginCount) {
            loginCount = (int) eventLoginCount;
        }

        List<UserAdminProfileDTO.LoginHistoryItemDTO> recentLogins = userLoginEventRepository
                .findByUserIdOrderByLoggedInAtDesc(userId, PageRequest.of(0, 15))
                .stream()
                .map(e -> UserAdminProfileDTO.LoginHistoryItemDTO.builder()
                        .loggedInAt(e.getLoggedInAt())
                        .build())
                .collect(Collectors.toList());

        List<UserCourseEnrollment> enrollments = enrollmentRepository.findByUserIdAndStatus(
                userId, UserCourseEnrollment.EnrollmentStatus.ACTIVE);
        List<UserAdminProfileDTO.CourseEnrollmentProfileDTO> courseEnrollments = enrollments.stream()
                .map(enrollment -> {
                    Course course = courseRepository.findById(enrollment.getCourseId()).orElse(null);
                    UserCourseProgress progress = progressRepository
                            .findByUserIdAndCourseId(userId, enrollment.getCourseId())
                            .orElse(null);
                    return UserAdminProfileDTO.CourseEnrollmentProfileDTO.builder()
                            .courseId(enrollment.getCourseId())
                            .courseName(course != null ? course.getName() : "Unknown")
                            .enrollmentType(enrollment.getEnrollmentType())
                            .enrolledAt(enrollment.getEnrolledAt())
                            .progress(progress != null ? progress.getProgress() : 0)
                            .lastAccessed(progress != null ? progress.getLastAccessed() : null)
                            .status(enrollment.getStatus())
                            .build();
                })
                .collect(Collectors.toList());

        List<UserAdminProfileDTO.ReviewSummaryDTO> recentReviews = reviewRepository
                .findByUserId(userId, PageRequest.of(0, 10))
                .getContent()
                .stream()
                .map(this::toReviewSummary)
                .collect(Collectors.toList());

        String chosenCourseId = user.getChosenFreemiumCourseId();
        String chosenCourseName = null;
        if (chosenCourseId != null && !chosenCourseId.isBlank()) {
            chosenCourseName = courseRepository.findById(chosenCourseId)
                    .map(Course::getName)
                    .orElse(null);
        }

        return UserAdminProfileDTO.builder()
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .planTier(user.getPlanTier())
                .role(user.getRole())
                .active(user.isActive())
                .createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .loginCount(loginCount)
                .queryCreditsUsed(user.getQueryCreditsUsed())
                .queryCreditsLimit(user.getQueryCreditsLimit())
                .enabledModules(user.getEnabledModules())
                .referralCode(user.getReferralCode())
                .referredBy(user.getReferredBy())
                .completedLecturesCount(completedCount)
                .totalQuestionsAsked(questionsAsked)
                .reviewCount((int) reviewRepository.findByUserId(userId, PageRequest.of(0, 1)).getTotalElements())
                .issueReportCount((int) issueReportRepository.countByReporterUserId(userId))
                .chosenFreemiumCourseId(chosenCourseId)
                .chosenFreemiumCourseName(chosenCourseName)
                .recentLogins(recentLogins)
                .courseEnrollments(courseEnrollments)
                .recentReviews(recentReviews)
                .build();
    }

    private UserAdminProfileDTO.ReviewSummaryDTO toReviewSummary(Review review) {
        return UserAdminProfileDTO.ReviewSummaryDTO.builder()
                .id(review.getId())
                .courseId(review.getCourseId())
                .rating(review.getRating())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt())
                .build();
    }

    public UserActivityDTO getUserActivity(String userId, int page, int size) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<QueryActivityLog> queryPage = queryActivityLogRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);

        UserProfile profile = userProfileRepository.findByUserId(userId).orElse(null);
        List<UserActivityDTO.LectureActivityItemDTO> lectures = new ArrayList<>();
        if (profile != null && profile.getCompletedLectures() != null) {
            lectures = profile.getCompletedLectures().stream()
                    .map(cl -> UserActivityDTO.LectureActivityItemDTO.builder()
                            .lectureLabel(cl.getLectureLabel())
                            .courseId(cl.getCourseId())
                            .completedAt(cl.getCompletedAt())
                            .build())
                    .collect(Collectors.toList());
        }

        List<UserActivityDTO.QueryActivityItemDTO> queryLog = queryPage.getContent().stream()
                .map(q -> UserActivityDTO.QueryActivityItemDTO.builder()
                        .queryType(q.getQueryType())
                        .courseId(q.getCourseId())
                        .createdAt(q.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return UserActivityDTO.builder()
                .userId(userId)
                .lastLoginAt(user.getLastLoginAt())
                .queryLog(queryLog)
                .lectureCompletions(lectures)
                .build();
    }

    private void initializeCourseProgress(String userId, String courseId) {
        if (progressRepository.findByUserIdAndCourseId(userId, courseId).isEmpty()) {
            UserCourseProgress progress = new UserCourseProgress();
            progress.setUserId(userId);
            progress.setCourseId(courseId);
            progress.setProgress(0);
            progress.setTotalLectures(0);
            progress.setCompletedLectures(0);
            progress.setEnrolledAt(IndiaTime.now());
            progress.setLastAccessed(IndiaTime.now());
            progress.setCreatedAt(IndiaTime.now());
            progress.setUpdatedAt(IndiaTime.now());
            progressRepository.save(progress);
        }
    }
    
    private UserDTO convertToUserDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setName(user.getName());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        dto.setPlanTier(user.getPlanTier());
        dto.setRole(user.getRole() != null ? user.getRole() : User.UserRole.USER);
        dto.setActive(user.isActive());
        dto.setGender(user.getGender() != null ? user.getGender().name() : null);
        dto.setCreatedAt(user.getCreatedAt());
        dto.setLastLoginAt(user.getLastLoginAt());
        dto.setLoginCount(user.getLoginCount() != null ? user.getLoginCount() : 0);
        dto.setCreatedBy(user.getCreatedBy());
        dto.setUpdatedAt(user.getUpdatedAt());
        dto.setUpdatedBy(user.getUpdatedBy());

        if (user.getId() != null) {
            long activeCourses = enrollmentRepository.countByUserIdAndStatus(
                    user.getId(), UserCourseEnrollment.EnrollmentStatus.ACTIVE);
            dto.setActiveCourseCount((int) activeCourses);

            List<UserCourseProgress> progressList = progressRepository.findByUserId(user.getId());
            dto.setAverageProgress(normalizeAverageProgressPercent(progressList));
        }

        return dto;
    }
    
    private List<String> getPermissions(User.UserRole role) {
        List<String> permissions = new ArrayList<>();
        
        if (role == User.UserRole.OWNER) {
            permissions.addAll(Arrays.asList(
                "CREATE_ADMIN", "CREATE_USER", "MANAGE_USERS",
                "CREATE_COURSE", "MANAGE_COURSES", "MANAGE_CURRICULUM",
                "ASSIGN_COURSES", "VIEW_ANALYTICS", "SYSTEM_SETTINGS"
            ));
        } else if (role == User.UserRole.ADMIN) {
            permissions.addAll(Arrays.asList(
                "CREATE_USER", "MANAGE_USERS",
                "CREATE_COURSE", "MANAGE_COURSES", "MANAGE_CURRICULUM",
                "ASSIGN_COURSES", "VIEW_ANALYTICS", "SYSTEM_SETTINGS"
            ));
        }
        
        return permissions;
    }
}


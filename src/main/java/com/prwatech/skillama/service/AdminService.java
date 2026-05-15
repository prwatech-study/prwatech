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
import com.prwatech.skillama.repository.CourseRepository;
import com.prwatech.skillama.repository.QueryActivityLogRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.repository.UserCourseEnrollmentRepository;
import com.prwatech.skillama.repository.UserCourseProgressRepository;
import com.prwatech.skillama.repository.UserProfileRepository;
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
    private final PasswordEncode passwordEncode;
    
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
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("Email already exists");
        }
        
        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncode.getEncryptedPassword(request.getPassword()));
        user.setRole(request.getRole() != null ? request.getRole() : User.UserRole.USER);
        user.setActive(request.getActive() != null ? request.getActive() : true);
        user.setGender(request.getGender());
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setCreatedBy(createdBy);
        user.setUpdatedBy(createdBy);
        
        user = userRepository.save(user);
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
        
        // Only OWNER can change roles to ADMIN/OWNER
        if ((request.getRole() == User.UserRole.ADMIN || request.getRole() == User.UserRole.OWNER)
            && updater.getRole() != User.UserRole.OWNER) {
            throw new RuntimeException("Only OWNER can change role to ADMIN or OWNER");
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
        if (request.getActive() != null) user.setActive(request.getActive());
        if (request.getGender() != null) user.setGender(request.getGender());
        
        user.setUpdatedAt(LocalDateTime.now());
        user.setUpdatedBy(updatedBy);
        
        user = userRepository.save(user);
        return convertToUserDTO(user);
    }
    
    @Transactional
    public void deleteUser(String userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        // Cannot delete OWNER
        if (user.getRole() == User.UserRole.OWNER) {
            throw new RuntimeException("Cannot delete OWNER user");
        }
        
        // Soft delete - set active to false
        user.setActive(false);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }
    
    @Transactional
    public AssignmentResponseDTO assignCourses(String userId, List<String> courseIds) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        List<UserCourseEnrollment> enrollments = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        
        for (String courseId : courseIds) {
            Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found: " + courseId));
            
            // Check if already enrolled
            if (!enrollmentRepository.existsByUserIdAndCourseId(userId, courseId)) {
                UserCourseEnrollment enrollment = new UserCourseEnrollment();
                enrollment.setUserId(userId);
                enrollment.setCourseId(courseId);
                enrollment.setEnrollmentType(UserCourseEnrollment.EnrollmentType.ASSIGNED);
                enrollment.setEnrolledAt(now);
                enrollment.setStatus(UserCourseEnrollment.EnrollmentStatus.ACTIVE);
                enrollments.add(enrollment);
            }
        }
        
        enrollmentRepository.saveAll(enrollments);
        
        // Initialize progress for new enrollments
        for (UserCourseEnrollment enrollment : enrollments) {
            initializeCourseProgress(enrollment.getUserId(), enrollment.getCourseId());
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
    public void unassignCourse(String userId, String courseId) {
        UserCourseEnrollment enrollment = enrollmentRepository
            .findByUserIdAndCourseId(userId, courseId)
            .orElseThrow(() -> new ResourceNotFoundException("Enrollment not found"));
        
        enrollment.setStatus(UserCourseEnrollment.EnrollmentStatus.INACTIVE);
        enrollmentRepository.save(enrollment);
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
                
                UserCourseProgress progress = progressRepository
                    .findByUserIdAndCourseId(enrollment.getUserId(), courseId)
                    .orElse(null);
                
                CourseAssignmentsDTO.UserAssignmentDTO dto = new CourseAssignmentsDTO.UserAssignmentDTO();
                dto.setUserId(enrollment.getUserId());
                dto.setUserName(user != null ? user.getName() : "Unknown");
                dto.setUserEmail(user != null ? user.getEmail() : "Unknown");
                dto.setEnrolledAt(enrollment.getEnrolledAt());
                dto.setProgress(progress != null ? progress.getProgress() : 0);
                
                return dto;
            })
            .collect(Collectors.toList());
        
        CourseAssignmentsDTO result = new CourseAssignmentsDTO();
        result.setCourseId(courseId);
        result.setCourseName(course.getName());
        result.setUsers(users);
        result.setTotalEnrollments(users.size());
        
        return result;
    }
    
    public DashboardStatsDTO getDashboardStatistics() {
        long totalUsers = userRepository.count();
        long activeUsers = userRepository.findAll().stream()
            .filter(User::isActive)
            .count();
        
        long totalCourses = courseRepository.count();
        long activeCourses = totalCourses; // Assuming all courses are active
        
        long totalEnrollments = enrollmentRepository.count();
        
        // Calculate average progress
        List<UserCourseProgress> allProgress = progressRepository.findAll();
        double averageProgress = allProgress.isEmpty() ? 0.0 :
            allProgress.stream()
                .mapToInt(p -> p.getProgress() != null ? p.getProgress() : 0)
                .average()
                .orElse(0.0);
        
        // Recent users (last 7 days) - simplified
        int recentUsers = (int) userRepository.findAll().stream()
            .filter(u -> u.getCreatedAt() != null 
                && u.getCreatedAt().isAfter(LocalDateTime.now().minusDays(7)))
            .count();
        
        // Recent courses (last 7 days) - simplified
        int recentCourses = (int) courseRepository.findAll().stream()
            .filter(c -> c.getCreatedAt() != null 
                && c.getCreatedAt().isAfter(LocalDateTime.now().minusDays(7)))
            .count();
        
        DashboardStatsDTO stats = new DashboardStatsDTO();
        stats.setTotalUsers(totalUsers);
        stats.setActiveUsers(activeUsers);
        stats.setTotalCourses(totalCourses);
        stats.setActiveCourses(activeCourses);
        stats.setTotalEnrollments(totalEnrollments);
        stats.setAverageProgress(averageProgress);
        stats.setRecentUsers(recentUsers);
        stats.setRecentCourses(recentCourses);
        
        return stats;
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

        return UserAdminProfileDTO.builder()
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .planTier(user.getPlanTier())
                .createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .queryCreditsUsed(user.getQueryCreditsUsed())
                .queryCreditsLimit(user.getQueryCreditsLimit())
                .enabledModules(user.getEnabledModules())
                .referralCode(user.getReferralCode())
                .referredBy(user.getReferredBy())
                .completedLecturesCount(completedCount)
                .totalQuestionsAsked(questionsAsked)
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
            progress.setEnrolledAt(LocalDateTime.now());
            progress.setLastAccessed(LocalDateTime.now());
            progress.setCreatedAt(LocalDateTime.now());
            progress.setUpdatedAt(LocalDateTime.now());
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
        dto.setCreatedBy(user.getCreatedBy());
        dto.setUpdatedAt(user.getUpdatedAt());
        dto.setUpdatedBy(user.getUpdatedBy());
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


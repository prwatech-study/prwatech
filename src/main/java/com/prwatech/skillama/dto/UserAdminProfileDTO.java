package com.prwatech.skillama.dto;

import com.prwatech.skillama.model.User;
import com.prwatech.skillama.model.UserCourseEnrollment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAdminProfileDTO {
    private String userId;
    private String name;
    private String email;
    private String phone;
    private User.PlanTier planTier;
    private User.UserRole role;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
    private int loginCount;
    private Integer queryCreditsUsed;
    private Integer queryCreditsLimit;
    private List<String> enabledModules;
    private String referralCode;
    private String referredBy;
    private int completedLecturesCount;
    private int totalQuestionsAsked;
    private int moduleQuizzesPassedCount;
    private int moduleQuizAttemptsCount;
    private int reviewCount;
    private int issueReportCount;
    private String chosenFreemiumCourseId;
    private String chosenFreemiumCourseName;

    private AiBudgetDTO aiBudget;
    private AiUsageUserDetailDTO aiUsageThisMonth;

    @Builder.Default
    private List<LoginHistoryItemDTO> recentLogins = new ArrayList<>();

    @Builder.Default
    private List<CourseEnrollmentProfileDTO> courseEnrollments = new ArrayList<>();

    @Builder.Default
    private List<ReviewSummaryDTO> recentReviews = new ArrayList<>();

    @Builder.Default
    private List<PassedModuleQuizSummaryDTO> passedModuleQuizzes = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PassedModuleQuizSummaryDTO {
        private String courseId;
        private String courseName;
        private String moduleName;
        private Integer bestScore;
        private Integer maxScore;
        private LocalDateTime passedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginHistoryItemDTO {
        private LocalDateTime loggedInAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CourseEnrollmentProfileDTO {
        private String courseId;
        private String courseName;
        private UserCourseEnrollment.EnrollmentType enrollmentType;
        private LocalDateTime enrolledAt;
        private Integer progress;
        private LocalDateTime lastAccessed;
        private UserCourseEnrollment.EnrollmentStatus status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReviewSummaryDTO {
        private String id;
        private String courseId;
        private Integer rating;
        private String comment;
        private LocalDateTime createdAt;
    }
}

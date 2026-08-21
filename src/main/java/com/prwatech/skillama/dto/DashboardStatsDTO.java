package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardStatsDTO {
    /** Learner accounts (USER role; null role counts as USER). */
    private Long totalUsers;
    /** Learners with {@code active == true} (can sign in). */
    private Long activeUsers;
    /** Learners with {@code active == false}. */
    private Long inactiveUsers;
    /** Distinct users with at least one login in the last 30 days (real behavioral activity, not account status). */
    private Long monthlyActiveUsers;
    private Long totalCourses;
    private Long activeCourses;
    private Long totalEnrollments;
    /** Average course progress 0–100 (one decimal). */
    private Double averageProgress;
    private Integer recentUsers;
    private Integer recentCourses;

    /** Average active listen time per topic/lecture (seconds). */
    private Double averageTopicTimeSeconds;
    /** Average AI response latency for chat/tutor queries (milliseconds). */
    private Double averageQueryResponseTimeMs;
    /** Average AI answer audio length (seconds). */
    private Double averageAnswerAudioSeconds;
    /** Average learner speak duration for voice questions (seconds). */
    private Double averageUserSpeakSeconds;
    private Long totalTopicTimeSamples;
    private Long totalQueryTimingSamples;

    @Builder.Default
    private List<TopCourseStatDTO> topCourses = new ArrayList<>();

    @Builder.Default
    private List<RecentLoginStatDTO> recentLogins = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TopCourseStatDTO {
        private String courseId;
        private String courseName;
        private long enrollmentCount;
        private double averageProgress;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RecentLoginStatDTO {
        private String userId;
        private String userName;
        private String userEmail;
        private LocalDateTime loggedInAt;
    }
}


package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Investor-facing metrics rollup. Every figure is MEASURED from stored data unless the
 * field name says otherwise — no assumed baselines here (those live on the efficiency page).
 * Cost figures are Bedrock token cost only; infrastructure/ops costs are out of scope.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvestorMetricsDTO {
    private LocalDate periodStart;
    private LocalDate periodEnd;

    private CostSection cost;
    private UsersSection users;
    private TrafficSection traffic;
    private ActivitySection activity;
    private GrowthSection growth;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CostSection {
        private double totalAiCostUsd;
        private double totalAiCostInr;
        private long totalTokens;
        /** Distinct signed-in users with at least one AI call this period. */
        private long activeAiUsers;
        private Double avgCostPerActiveUserUsd;
        private Double avgCostPerActiveUserInr;
        /** ALL-TIME token cost divided by ALL-TIME recorded learning hours. */
        private Double costPerLearningHourUsd;
        private Double costPerLearningHourInr;
        /** Period cost of content-creation endpoints (lecture/diagram/thumbnail generation). */
        private double contentCreationCostUsd;
        /** Period cost of learner-serving endpoints (chat, quiz, exam, debug...). */
        private double learnerServingCostUsd;
        @Builder.Default
        private List<CourseCostRowDTO> perCourse = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CourseCostRowDTO {
        private String courseId;
        private String courseName;
        private double totalCostUsd;
        private double totalCostInr;
        private double creationCostUsd;
        private double learnerCostUsd;
        private long distinctUsers;
        private Double avgCostPerUserUsd;
        private long totalTokens;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UsersSection {
        /** All-time recorded active learning hours across all learners. */
        private double totalLearningHoursAllTime;
        private long learnersWithLearningActivity;
        private Double avgLearningHoursPerLearner;
        private Double onboardingCompletionRatePercent;
        /** Average active listen time per lecture (seconds) — from admin dashboard stats. */
        private Double averageTopicTimeSeconds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrafficSection {
        private long dau;
        private long wau;
        private long mau;
        /** DAU/MAU stickiness, percent. */
        private Double dauMauRatioPercent;
        private long signupsLast7Days;
        private long signupsLast30Days;
        private long totalLearners;
        private long activeLearners;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivitySection {
        private long totalCourses;
        private long coursesCreatedThisPeriod;
        private long totalEnrollments;
        @Builder.Default
        private Map<String, Long> enrollmentsByType = new LinkedHashMap<>();
        private Double averageCourseProgressPercent;
        private long quizzesTakenThisPeriod;
        private Double quizPassRatePercent;
        private Double quizAvgScorePercent;
        private long examsTakenThisPeriod;
        private Double examAvgScorePercent;
        private long doubtsResolvedThisPeriod;
        private Double averageQueryResponseTimeMs;
        /** Thumbs-up share of AI answer votes this period; null until votes exist. */
        private Double aiHelpfulRatePercent;
        private long aiFeedbackVotesThisPeriod;
        private long aiFeedbackVotesAllTime;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GrowthSection {
        private long referralConversionsAllTime;
        private long referralConversionsThisPeriod;
        /** Share of all learners whose signup carried a referral code, percent. */
        private Double referralSignupSharePercent;
        @Builder.Default
        private Map<String, Long> referralSharesByChannel = new LinkedHashMap<>();
        @Builder.Default
        private Map<String, Long> courseSharesByPlatform = new LinkedHashMap<>();
        /** Of learners who signed up 7-37 days ago, share with a login >= 7 days after signup. */
        private Double d7RetentionPercent;
        private long d7CohortSize;
        /** Of learners who signed up 30-60 days ago, share with a login >= 30 days after signup. */
        private Double d30RetentionPercent;
        private long d30CohortSize;
    }
}

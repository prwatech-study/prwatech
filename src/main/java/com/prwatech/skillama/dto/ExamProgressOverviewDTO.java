package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Cross-attempt AI Exam progress for one course — the "how am I doing overall"
 * view, as opposed to {@link ExamResultDashboardDTO}'s single-attempt view.
 * All fields are computed fresh from this learner's stored attempts; empty
 * lists/zero counts (not fabricated data) when there's no history yet.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamProgressOverviewDTO {
    private String courseId;
    private String courseName;

    private Integer totalAttempts;
    private Double averagePercentage;
    /** % of attempts scoring at/above the passing threshold. Null when totalAttempts is 0. */
    private Double passRate;
    private Double bestPercentage;
    private Integer passingPercentage;

    /** Oldest first. */
    private List<ScoreTrendPointDTO> scoreTrend;
    private List<ExamTypeStatDTO> examTypeBreakdown;

    /** Same computation as ExamResultDashboardDTO#focusAreas — empty until a TOPIC_WISE/MODULE_WISE exam exists. */
    private List<FocusAreaDTO> focusAreas;
}

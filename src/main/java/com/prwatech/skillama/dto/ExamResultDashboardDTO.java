package com.prwatech.skillama.dto;

import com.prwatech.skillama.model.ExamDifficulty;
import com.prwatech.skillama.model.ExamType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Full learner-facing Result Dashboard for one attempt — assembled fresh on
 * every fetch (never cached at submission time) so rank and focus areas stay
 * current as more attempts accumulate across the platform.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamResultDashboardDTO {
    private String attemptId;
    private String courseId;
    private String courseName;
    private String moduleId;
    private String topic;
    private ExamDifficulty difficulty;
    private ExamType examType;

    private Integer score;
    private Integer maxScore;
    private Double percentage;
    private Boolean passed;
    private Integer passingPercentage;

    private Integer timeSpentSeconds;
    private Boolean overTimeLimit;
    private LocalDateTime submittedAt;

    private List<ExamAnswerResultDTO> answers;

    private String overallFeedback;
    private String recommendationText;

    /** Null when the cohort is too small for a meaningful percentile. */
    private RankStandingDTO rank;

    /** Empty when the learner hasn't taken any TOPIC_WISE/MODULE_WISE exams yet for this course. */
    private List<FocusAreaDTO> focusAreas;

    private RetakeOptionsDTO retakeOptions;
}

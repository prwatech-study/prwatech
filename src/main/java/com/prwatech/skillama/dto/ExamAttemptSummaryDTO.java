package com.prwatech.skillama.dto;

import com.prwatech.skillama.model.ExamDifficulty;
import com.prwatech.skillama.model.ExamType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamAttemptSummaryDTO {
    private String attemptId;
    private String courseId;
    private String moduleId;
    private String topic;
    private ExamDifficulty difficulty;
    private ExamType examType;
    private Integer score;
    private Integer maxScore;
    private Double percentage;
    private Integer timeSpentSeconds;
    private Boolean overTimeLimit;
    private LocalDateTime submittedAt;
}

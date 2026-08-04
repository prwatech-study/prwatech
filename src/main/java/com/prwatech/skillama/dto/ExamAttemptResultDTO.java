package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamAttemptResultDTO {
    private String attemptId;
    private Integer score;
    private Integer maxScore;
    private Double percentage;
    private Boolean passed;
    private Integer passingPercentage;
    private Integer timeSpentSeconds;
    private Boolean overTimeLimit;
    private List<ExamAnswerResultDTO> answers;
}

package com.prwatech.skillama.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModuleQuizAttemptSummaryDTO {
    private String attemptId;
    private String courseId;
    private String moduleName;
    private Integer attemptNumber;
    private Integer score;
    private Integer maxScore;
    private Double percentage;
    private Boolean passed;
    private Integer timeSpentSeconds;
    private LocalDateTime submittedAt;
}

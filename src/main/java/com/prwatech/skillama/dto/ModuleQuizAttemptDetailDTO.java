package com.prwatech.skillama.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModuleQuizAttemptDetailDTO {
    private String attemptId;
    private String courseId;
    private String courseName;
    private String moduleName;
    private Integer attemptNumber;
    private Integer score;
    private Integer maxScore;
    private Double percentage;
    private Boolean passed;
    private Integer passingPercentage;
    private Integer timeSpentSeconds;
    private LocalDateTime submittedAt;

    @Builder.Default
    private List<ModuleQuizAnswerResultDTO> answers = new ArrayList<>();
}

package com.prwatech.skillama.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModuleQuizAttemptResultDTO {
    private String attemptId;
    private Integer attemptNumber;
    private Integer score;
    private Integer maxScore;
    private Double percentage;
    private Boolean passed;
    private Integer passingPercentage;
    private List<ModuleQuizAnswerResultDTO> answers;
}

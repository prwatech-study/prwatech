package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Admin view of a single Module Quiz attempt. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminModuleQuizAttemptDTO {
    private String attemptId;
    private String userId;
    private String userName;
    private String userEmail;
    private String courseId;
    private String courseName;
    private String moduleName;
    private Integer attemptNumber;
    private Integer score;
    private Integer maxScore;
    private Double percentage;
    private Boolean passed;
    private Integer timeSpentSeconds;
    private Boolean overTimeLimit;
    private LocalDateTime submittedAt;
}

package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgressSummaryDTO {
    private Integer totalLectures;
    private Integer completedLectures;
    private Integer inProgressLectures;
    private Integer lockedLectures;
    private Integer completionPercentage;
    /** One quiz gate per module with enabled lectures. */
    private Integer totalModuleQuizzes;
    private Integer passedModuleQuizzes;
    private Integer pendingModuleQuizzes;
}


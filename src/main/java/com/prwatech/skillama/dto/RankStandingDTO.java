package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * How this attempt compares to other attempts of the same courseId + examType
 * + difficulty. Only ever returned once the cohort is large enough to make a
 * percentile meaningful — see {@code ExamService#computeCohortStanding}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RankStandingDTO {
    private Integer percentile;
    private Integer topPercent;
    private Integer cohortSize;
}

package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Estimated time/cost saved by AI handling quiz/exam generation and doubt
 * resolution, computed as AI-handled volume × admin-configured assumed
 * manual baseline. Not a measured fact — no historical human-timing data
 * exists to measure against, so every field here is explicitly an estimate.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EfficiencyEstimateDTO {
    private String period;
    private long quizzesGenerated;
    private long examsGenerated;
    private long doubtsResolved;
    private double estimatedMinutesSaved;
    private double estimatedHoursSaved;
    private double estimatedCostSavedInr;
}

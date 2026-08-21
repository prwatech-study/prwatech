package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EfficiencyAssumptionsDTO {
    private double assumedManualQuizCreationMinutes;
    private double assumedManualExamCreationMinutes;
    private double assumedManualDoubtResolutionMinutes;
    private double assumedHourlyInstructorCostInr;
    private LocalDateTime updatedAt;
}

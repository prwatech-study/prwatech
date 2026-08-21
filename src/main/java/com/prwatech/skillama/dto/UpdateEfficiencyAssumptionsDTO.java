package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateEfficiencyAssumptionsDTO {
    private Double assumedManualQuizCreationMinutes;
    private Double assumedManualExamCreationMinutes;
    private Double assumedManualDoubtResolutionMinutes;
    private Double assumedHourlyInstructorCostInr;
}

package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAiUsageSettingsDTO {
    private Boolean aiUsageTrackingEnabled;
    private Double platformMonthlyBudgetUsd;
    private Double freemiumMonthlyBudgetUsdPerUser;
}

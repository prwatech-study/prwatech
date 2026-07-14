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
public class AiUsageSettingsDTO {
    private boolean aiUsageTrackingEnabled;
    private double platformMonthlyBudgetUsd;
    private double freemiumMonthlyBudgetUsdPerUser;
    private double usdToInrRate;
    private LocalDateTime updatedAt;
}

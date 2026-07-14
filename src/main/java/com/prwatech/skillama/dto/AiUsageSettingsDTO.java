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
    /** Live USD → INR from FX API (read-only). */
    private double usdToInrRate;
    /** Date the FX provider quotes for the rate (e.g. ECB publication date). */
    private String usdToInrRateAsOf;
    private LocalDateTime updatedAt;
}

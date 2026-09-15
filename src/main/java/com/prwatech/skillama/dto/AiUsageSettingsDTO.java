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
    private double referralRewardUsd;
    private double courseShareRewardUsd;
    /** Wallet burn rate vs rate-card API cost. Owner-only. Minimum 1.0. */
    private double consumptionMultiplier;
    /** Live USD → INR from FX API (read-only). */
    private double usdToInrRate;
    /** Date the FX provider quotes for the rate (e.g. ECB publication date). */
    private String usdToInrRateAsOf;
    private LocalDateTime updatedAt;
}

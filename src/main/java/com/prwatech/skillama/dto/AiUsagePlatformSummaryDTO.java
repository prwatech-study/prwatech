package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiUsagePlatformSummaryDTO {
    private String period;
    private LocalDateRangeDTO dateRange;
    private long totalInputTokens;
    private long totalOutputTokens;
    private long totalTokens;
    private double totalCostUsd;
    private double totalCostInr;
    private double platformMonthlyBudgetUsd;
    private double budgetRemainingUsd;
    private double budgetUtilizationPercent;
    private long activeUsersWithUsage;
    private long totalActiveUsers;
    private double avgCostPerUserUsd;
    private double avgCostPerUserInr;
    private double avgCostPerUserPerDayUsd;
    private double avgCostPerUserPerDayInr;
    private int daysElapsedInPeriod;
    private double projectedMonthEndCostUsd;
    private double usdToInrRate;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocalDateRangeDTO {
        private String start;
        private String end;
    }
}

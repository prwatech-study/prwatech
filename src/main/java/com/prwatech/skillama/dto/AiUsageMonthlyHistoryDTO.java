package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Calendar-month rollup of rate-card AI API cost (Bedrock / Transcribe / Polly).
 * Infrastructure (EC2, DB, etc.) is not included.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiUsageMonthlyHistoryDTO {
    private double lifetimeTotalCostUsd;
    private double lifetimeTotalCostInr;
    private double lifetimeTotalBilledUsd;
    private double lifetimeTotalBilledInr;
    private long lifetimeUsersWithUsage;
    private double lifetimeAvgCostPerUserUsd;
    private double lifetimeAvgCostPerUserInr;
    private double usdToInrRate;
    private double consumptionMultiplier;
    private List<MonthRowDTO> months;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthRowDTO {
        /** ISO year-month, e.g. {@code 2026-09}. */
        private String yearMonth;
        /** Display label, e.g. {@code Sep 2026}. */
        private String label;
        private String start;
        private String end;
        private double totalCostUsd;
        private double totalCostInr;
        private double totalBilledUsd;
        private double totalBilledInr;
        private long activeUsersWithUsage;
        private double avgCostPerUserUsd;
        private double avgCostPerUserInr;
        private long totalTokens;
        private long inputTokens;
        private long outputTokens;
    }
}

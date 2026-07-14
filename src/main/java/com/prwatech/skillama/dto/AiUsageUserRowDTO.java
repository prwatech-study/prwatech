package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiUsageUserRowDTO {
    private String userId;
    private String name;
    private String email;
    private long inputTokens;
    private long outputTokens;
    private long totalTokens;
    private double costUsd;
    private double costInr;
    private Double freemiumBudgetUsd;
    private Double budgetUsedPercent;
}

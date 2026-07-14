package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiUsageUserDetailDTO {
    private String userId;
    private String name;
    private String email;
    private long inputTokens;
    private long outputTokens;
    private long totalTokens;
    private double costUsd;
    private double costInr;
    private AiBudgetDTO aiBudget;

    @Builder.Default
    private List<EndpointBreakdownDTO> byEndpoint = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EndpointBreakdownDTO {
        private String endpoint;
        private long inputTokens;
        private long outputTokens;
        private double costUsd;
        private long callCount;
    }
}

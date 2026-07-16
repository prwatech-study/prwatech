package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Priced cost of a single AI call, for display (no usage event persisted). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiCostEstimateDTO {
    private Double costUsd;
    private Double costInr;
    private Double usdToInrRate;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer totalTokens;
}

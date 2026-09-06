package com.prwatech.skillama.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class KnowledgeBaseSyncResponseDTO {
    String ingestionJobId;
    String status;
    double estimatedCostUsd;
    long pendingTokens;
}

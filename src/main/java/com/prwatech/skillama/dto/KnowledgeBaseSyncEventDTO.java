package com.prwatech.skillama.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.Map;

@Value
@Builder
public class KnowledgeBaseSyncEventDTO {
    String id;
    String courseId;
    String ingestionJobId;
    String triggeredBy;
    LocalDateTime triggeredAt;
    String status;
    Integer fileCountAtSync;
    Double estCostUsd;
    Map<String, Object> statistics;
}

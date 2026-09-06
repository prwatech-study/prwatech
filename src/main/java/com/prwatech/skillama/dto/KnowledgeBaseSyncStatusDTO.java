package com.prwatech.skillama.dto;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Value
@Builder
public class KnowledgeBaseSyncStatusDTO {
    String status;
    Map<String, Object> statistics;
}

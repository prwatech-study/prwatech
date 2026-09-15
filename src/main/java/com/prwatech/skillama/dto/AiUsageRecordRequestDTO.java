package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiUsageRecordRequestDTO {
    private String userId;
    private String sessionId;
    private String endpoint;
    private String modelId;
    private String courseId;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer totalTokens;
    /** Amazon Transcribe streaming duration in seconds (actual audio length). */
    private Double audioSeconds;
    /** Amazon Polly billed characters (SSML tags excluded). */
    private Integer pollyCharacters;
}

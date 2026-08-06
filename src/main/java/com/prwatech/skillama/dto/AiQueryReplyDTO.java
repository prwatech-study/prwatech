package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Result of a backend-initiated free-text query (Chat/AI Mentor) to the ai-tutor service. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiQueryReplyDTO implements AiGenerationUsage {
    private String responseText;
    private String audioUrl;
    private String subtitlePath;
    private String modelId;
    private int inputTokens;
    private int outputTokens;
    private int totalTokens;
}

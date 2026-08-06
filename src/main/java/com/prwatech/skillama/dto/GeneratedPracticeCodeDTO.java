package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Result of a backend-initiated practice-code generation call to the ai-tutor service. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneratedPracticeCodeDTO implements AiGenerationUsage {
    private String codeResult;
    private String audioUrl;
    private String subtitlePath;
    private String modelId;
    private int inputTokens;
    private int outputTokens;
    private int totalTokens;
}

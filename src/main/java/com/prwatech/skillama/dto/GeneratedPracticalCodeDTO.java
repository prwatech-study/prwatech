package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Result of a backend-initiated practical-exercise code-generation call to ai-tutor. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneratedPracticalCodeDTO implements AiGenerationUsage {
    private String code;
    private String modelId;
    private int inputTokens;
    private int outputTokens;
    private int totalTokens;
}

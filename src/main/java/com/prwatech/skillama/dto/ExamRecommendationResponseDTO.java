package com.prwatech.skillama.dto;

import com.prwatech.skillama.model.ExamDifficulty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Best-effort AI-generated recommendation — parsed from a free-text LLM reply
 * (there is no dedicated recommendation endpoint on the ai-tutor service), so
 * every field falls back to a sensible default if the model's reply didn't
 * follow the requested format.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamRecommendationResponseDTO implements AiGenerationUsage {
    private ExamDifficulty difficulty;
    private String topic;
    private String reasoning;
    private Integer estimatedMinutes;
    private Integer expectedScorePercent;

    // Internal — used to record AI usage server-side; harmless if it reaches the client.
    private String modelId;
    private int inputTokens;
    private int outputTokens;
    private int totalTokens;
}

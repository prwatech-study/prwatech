package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Best-effort AI-generated post-exam feedback — parsed from a free-text LLM
 * reply (there is no dedicated feedback endpoint on the ai-tutor service), so
 * every field falls back to a sensible default if the model's reply didn't
 * follow the requested format. Generated once at submission and stored on
 * the {@link com.prwatech.skillama.model.ExamAttempt} — never regenerated.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamFeedbackResponseDTO {
    private String overallFeedback;
    private String recommendationText;

    // Internal — used to record AI usage server-side; harmless if it reaches the client.
    private String modelId;
    private int inputTokens;
    private int outputTokens;
    private int totalTokens;
}

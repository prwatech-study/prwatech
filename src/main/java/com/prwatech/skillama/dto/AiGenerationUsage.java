package com.prwatech.skillama.dto;

/**
 * Common shape every ai-tutor generation result DTO exposes, so SkillamaAiClient's metered
 * call wrapper can record cost/usage generically regardless of which endpoint produced it.
 */
public interface AiGenerationUsage {
    String getModelId();
    int getInputTokens();
    int getOutputTokens();
    int getTotalTokens();
}

package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Result of a backend-initiated certification exam generation call to ai-tutor. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneratedCertificationExamDTO implements AiGenerationUsage {
    private String examTitle;
    private Integer timeLimitSeconds;
    private List<ModuleQuizQuestionDTO> questions;
    private String modelId;
    private int inputTokens;
    private int outputTokens;
    private int totalTokens;
}

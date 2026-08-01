package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Result of a backend-initiated quiz generation call to the ai-tutor service. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneratedQuizDTO {
    private String quizTitle;
    private List<ModuleQuizQuestionDTO> questions;
    private String modelId;
    private int inputTokens;
    private int outputTokens;
    private int totalTokens;
}

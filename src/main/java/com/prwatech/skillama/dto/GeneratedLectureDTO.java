package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Result of a backend-initiated lecture-generation call to the ai-tutor service. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneratedLectureDTO implements AiGenerationUsage {
    private String lectureText;
    private String audioUrl;
    private String subtitlePath;
    private String modelId;
    private int inputTokens;
    private int outputTokens;
    private int totalTokens;
}

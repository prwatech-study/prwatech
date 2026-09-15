package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Result of a backend-initiated course-detail copy call to ai-tutor. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneratedCourseDetailDTO implements AiGenerationUsage {
    private String tagline;
    private String overview;
    private String description;
    private List<String> objectives;
    private List<String> highlights;
    private List<String> prerequisites;
    private List<String> outcomes;
    private String audience;
    private String aiTutorHelp;
    private String modelId;
    private int inputTokens;
    private int outputTokens;
    private int totalTokens;
}

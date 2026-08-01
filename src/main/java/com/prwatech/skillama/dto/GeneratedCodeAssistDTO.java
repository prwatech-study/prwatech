package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Result of a backend-initiated Debug/Code Execution call to the ai-tutor service. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneratedCodeAssistDTO {
    private String codeOutput;
    private String correctedCode;
    private String responseText;
    private String audioUrl;
    private String subtitlePath;
    private String modelId;
    private int inputTokens;
    private int outputTokens;
    private int totalTokens;
}

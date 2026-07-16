package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Result of calling the ai-tutor /generate_image endpoint (one diagram candidate). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneratedImageDTO {
    private String imageBase64;
    private String contentType;
    private String diagramType;
    private String title;
    private String modelId;
    private int inputTokens;
    private int outputTokens;
    private int totalTokens;
}

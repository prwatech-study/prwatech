package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single AI-generated image candidate returned to the admin (preview only — NOT
 * yet saved). The admin picks one and commits it via the /commit endpoint. Carries
 * the per-generation cost and the daily-cap counters for the UI.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageGenerateResponseDTO {
    private String moduleId;
    private int submoduleIndex;

    // Candidate payload (held client-side until committed).
    private String imageBase64;
    private String contentType;
    private String diagramType;
    private String title;

    // Cost of THIS generation (for "this image cost ₹X" display).
    private Double costUsd;
    private Double costInr;
    private Double usdToInrRate;
    private Integer totalTokens;

    // Daily-cap state (server-authoritative).
    private int triesUsedToday;
    private int triesRemainingToday;
    private int dailyCap;
}

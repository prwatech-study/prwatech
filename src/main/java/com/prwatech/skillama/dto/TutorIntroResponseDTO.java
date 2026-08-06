package com.prwatech.skillama.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Matches the raw ai-tutor /introduce_tutor response shape (no LLM call — templated TTS). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TutorIntroResponseDTO {
    @JsonProperty("introduction_text")
    private String introductionText;

    @JsonProperty("audio_url")
    private String audioUrl;

    @JsonProperty("subtitle_path")
    private String subtitlePath;
}

package com.prwatech.skillama.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Matches the raw ai-tutor /confirmation response shape (Transcribe + keyword/LLM fallback). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmationResponseDTO {
    @JsonProperty("response_text")
    private Boolean responseText;

    @JsonProperty("audio_url")
    private String audioUrl;

    @JsonProperty("subtitle_path")
    private String subtitlePath;

    private String transcript;
}

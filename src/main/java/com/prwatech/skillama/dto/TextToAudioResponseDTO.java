package com.prwatech.skillama.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Matches the raw ai-tutor /text_to_audio response shape (generic TTS, no LLM call). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TextToAudioResponseDTO {
    @JsonProperty("audio_url")
    private String audioUrl;

    @JsonProperty("subtitle_path")
    private String subtitlePath;
}

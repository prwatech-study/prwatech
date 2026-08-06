package com.prwatech.skillama.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Matches the raw ai-tutor /get_user_name response shape (Transcribe + name-extraction LLM call). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserNameResponseDTO {
    @JsonProperty("welcome_text")
    private String welcomeText;

    @JsonProperty("audio_url")
    private String audioUrl;

    @JsonProperty("subtitle_path")
    private String subtitlePath;
}

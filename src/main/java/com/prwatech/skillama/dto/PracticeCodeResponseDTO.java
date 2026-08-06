package com.prwatech.skillama.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Practice-code result returned to the client. Field names match the raw ai-tutor response
 * ({@code code_result}/{@code audio_url}/{@code subtitle_path}) so CodeExecution.js, written
 * against the old browser-direct call, doesn't need to change alongside this endpoint moving
 * behind the backend.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PracticeCodeResponseDTO {
    @JsonProperty("code_result")
    private String codeResult;

    @JsonProperty("audio_url")
    private String audioUrl;

    @JsonProperty("subtitle_path")
    private String subtitlePath;
}

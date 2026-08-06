package com.prwatech.skillama.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Chat reply returned to the client. Field names match the raw ai-tutor response
 * ({@code response_text}/{@code audio_url}/{@code subtitle_path}) so the existing chat UI
 * (ChatPanel.js, AiTutor.js), written against the old browser-direct call, doesn't need to
 * change shape-wise alongside this endpoint moving behind the backend.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatAskResponseDTO {
    @JsonProperty("response_text")
    private String responseText;

    @JsonProperty("audio_url")
    private String audioUrl;

    @JsonProperty("subtitle_path")
    private String subtitlePath;
}

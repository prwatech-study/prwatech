package com.prwatech.skillama.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lecture-generation result returned to the client. Field names match the raw ai-tutor
 * response ({@code lecture_text}/{@code audio_url}/{@code subtitle_path}) so the existing
 * frontend consumers (AiTutor.js etc., written against the old browser-direct call) don't
 * need to change alongside this endpoint moving behind the backend.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LectureResponseDTO {
    @JsonProperty("lecture_text")
    private String lectureText;

    @JsonProperty("audio_url")
    private String audioUrl;

    @JsonProperty("subtitle_path")
    private String subtitlePath;
}

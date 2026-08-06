package com.prwatech.skillama.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Matches the raw ai-tutor /lecture_start_instruction response shape (templated TTS, no LLM call). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LectureStartInstructionResponseDTO {
    @JsonProperty("lecture_introduction_text")
    private String lectureIntroductionText;

    @JsonProperty("audio_url")
    private String audioUrl;

    @JsonProperty("subtitle_path")
    private String subtitlePath;
}

package com.prwatech.skillama.dto;

import lombok.Data;

/**
 * Starts a new AI Mentor doubt. The LLM call already happened client-side (same
 * pattern as chat/track) — this submits the resulting question+answer pair for
 * persistence and wallet accounting.
 */
@Data
public class AskDoubtRequestDTO {
    private String courseId;
    private String moduleId;
    private String lessonId;
    private String question;
    private String answer;
}

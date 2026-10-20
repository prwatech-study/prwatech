package com.prwatech.skillama.dto;

import lombok.Data;

/** Starts a new AI Mentor doubt — the backend generates the answer (see DoubtService). */
@Data
public class AskDoubtRequestDTO {
    private String courseId;
    private String moduleId;
    private String lessonId;
    private String question;

    /**
     * Optional screenshot/diagram for multimodal Mentor answers.
     * Raw base64 (no data-URL prefix required). Not persisted — only forwarded to AI.
     */
    private String imageBase64;

    /** e.g. {@code image/png}, {@code image/jpeg}, {@code image/webp}. Required when imageBase64 is set. */
    private String imageContentType;
}

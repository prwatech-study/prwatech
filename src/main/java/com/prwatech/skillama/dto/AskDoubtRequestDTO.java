package com.prwatech.skillama.dto;

import lombok.Data;

/** Starts a new AI Mentor doubt — the backend generates the answer (see DoubtService). */
@Data
public class AskDoubtRequestDTO {
    private String courseId;
    private String moduleId;
    private String lessonId;
    private String question;
}

package com.prwatech.skillama.dto;

import lombok.Data;

@Data
public class GlobalAiExamCourseRequestDTO {
    /** Existing catalog course id. Optional when {@code name} is provided. */
    private String courseId;
    /** Free-text subject (not required to exist as a Skillama course). */
    private String name;
    private String description;
}

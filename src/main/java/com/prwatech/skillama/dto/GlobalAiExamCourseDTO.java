package com.prwatech.skillama.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GlobalAiExamCourseDTO {
    /** Config row id — used for PUT/DELETE. */
    private String id;
    /** Real course id — used to start an exam. */
    private String courseId;
    private String name;
    private String thumbnail;
    private String description;
    /** False when the course is missing, archived, or deactivated. */
    private boolean available;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    private String createdBy;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;

    private String updatedBy;
}

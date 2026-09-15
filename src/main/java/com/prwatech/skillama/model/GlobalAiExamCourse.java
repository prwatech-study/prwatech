package com.prwatech.skillama.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Platform-wide AI Examination eligibility: one row per course.
 * Courses on this list are visible to every logged-in user regardless of enrollment.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "global_ai_exam_courses")
public class GlobalAiExamCourse {

    @Id
    private String id;

    @Indexed(unique = true)
    private String courseId;

    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
}

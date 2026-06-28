package com.prwatech.skillama.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "courses")
public class Course {
    @Id
    private String id;
    private String name;
    private String description;
    private String thumbnail; // Course thumbnail image URL (optional)
    private String createdBy;
    private String updatedBy;
    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime updatedAt;
    @Builder.Default
    private Boolean isGuestCourse = Boolean.FALSE; // Default guest course flag
    @Builder.Default
    private Boolean isPublic = Boolean.FALSE; // Public course flag for guest access
    /**
     * Optional label for AI Tutor Flask payloads ({@code course} JSON field).
     * When unset, the LMS uses {@link #name}.
     */
    private String aiCourseName;
    /**
     * Runnable code output: {@link CourseCodeOutputMode#COMPILER} (real Python runner)
     * or {@link CourseCodeOutputMode#AI} (LLM-simulated). Source of truth for LMS routing.
     */
    @Builder.Default
    private String codeOutputMode = CourseCodeOutputMode.AI;
    /** When set, course is hidden from learners and admins; only Owner can restore. */
    private java.time.LocalDateTime deletedAt;
    private String deletedBy;
    private java.time.LocalDateTime restoredAt;
    private String restoredBy;
    // Removed curriculum field; curriculum is stored in CourseCurriculum collection

    public boolean isDeleted() {
        return deletedAt != null;
    }
}

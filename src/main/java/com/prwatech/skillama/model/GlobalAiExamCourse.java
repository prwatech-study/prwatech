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
 * Platform-wide AI Examination eligibility: one row per catalog course or custom subject.
 * Entries are visible to every logged-in user regardless of enrollment.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "global_ai_exam_courses")
public class GlobalAiExamCourse {

    public static final String CUSTOM_ID_PREFIX = "custom-";

    @Id
    private String id;

    /** Catalog course id, or {@code custom-<slug>} for a free-text subject. */
    @Indexed(unique = true)
    private String courseId;

    /** Display name: catalog snapshot or the custom subject the admin typed. */
    private String name;

    private String description;

    /** Lowercased collapsed name for duplicate checks. Sparse so legacy rows without it can coexist. */
    @Indexed(unique = true, sparse = true)
    private String nameKey;

    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
}

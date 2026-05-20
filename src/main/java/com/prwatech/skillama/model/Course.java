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
    /** Auto-assigned to every new freemium user; only one course should have this flag. */
    @Builder.Default
    private Boolean isDefaultFreemiumCourse = Boolean.FALSE;
    // Removed curriculum field; curriculum is stored in CourseCurriculum collection
}

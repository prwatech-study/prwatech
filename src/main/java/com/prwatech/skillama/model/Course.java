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
    // AI thumbnail generation daily-cap counters (mirrors Submodule.imageGenCount*).
    private Integer thumbnailGenCountToday;
    private String thumbnailGenCountDate; // ISO yyyy-MM-dd, India time
    private String createdBy;
    private String updatedBy;
    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime updatedAt;
    @Builder.Default
    private Boolean isGuestCourse = Boolean.FALSE; // Default guest course flag
    @Builder.Default
    private Boolean isPublic = Boolean.FALSE; // Public course flag for guest access
    /** When false, the course is hidden from learners and not available for assignment. Distinct from archive (deletedAt). */
    @Builder.Default
    private Boolean active = Boolean.TRUE;
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

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
    /**
     * Public no-login DEMO course: fully unlocked (all lectures + all features) for
     * anonymous visitors, capped by a shared daily AI-spend budget. Distinct from
     * the guest teaser (isGuestCourse), which only unlocks the first lecture.
     */
    @Builder.Default
    private Boolean isDemo = Boolean.FALSE;
    /** When false, the course is hidden from learners and not available for assignment. Distinct from archive (deletedAt). */
    @Builder.Default
    private Boolean active = Boolean.TRUE;
    /**
     * When false, the course is hidden from the registration/signup course picker only —
     * it remains assignable by admins and fully usable by already-enrolled learners.
     * Invariant enforced in CourseService: can only be true when active is also true
     * (a course visible at registration must always be assignable).
     */
    @Builder.Default
    private Boolean registrationEligible = Boolean.TRUE;
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

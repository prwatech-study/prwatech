package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One card on the learner "Explore courses" catalog. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseCatalogItemDTO {
    private String courseId;
    private String name;
    private String description;
    private String thumbnail;
    /** Learner already has an ACTIVE enrollment — show "Start learning". */
    private boolean enrolled;
    /** PENDING / APPROVED / DENIED of the learner's latest request, null when none. */
    private String requestStatus;
    /** Admin's reason when the latest request was denied. */
    private String decisionReason;
    /** ACTIVE enrollments across all learners — the "popularity" recommendation signal. */
    private long enrollmentCount;
}

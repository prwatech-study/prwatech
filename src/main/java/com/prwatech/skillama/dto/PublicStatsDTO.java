package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Public marketing aggregates — no PII, safe for unauthenticated homepage. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicStatsDTO {
    /** Learners with effective role USER (same definition as admin dashboard). */
    private long learnerCount;
    /** Active public courses available without login. */
    private long publicCourseCount;
    /** All active (non-archived) courses on the platform. */
    private long activeCourseCount;
}

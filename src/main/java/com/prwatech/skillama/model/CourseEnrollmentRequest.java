package com.prwatech.skillama.model;

import com.prwatech.skillama.util.IndiaTime;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * A learner's request (from the Explore catalog) to be enrolled in a course.
 * Learners cannot self-enroll. Organization members are approved by the org
 * owner or org admin; individual (B2C) learners stay on the Skillama admin queue.
 * One PENDING request per (user, course) is enforced in the service.
 */
@Data
@Document(collection = "course_enrollment_requests")
public class CourseEnrollmentRequest {
    @Id
    private String id;

    @Indexed
    private String userId;

    @Indexed
    private String courseId;

    /**
     * Set when the requester belongs to a corporate tenant. Null for individual / B2C
     * learners — those stay on the Skillama admin queue.
     */
    @Indexed
    private String organizationId;

    @Indexed
    private RequestStatus status = RequestStatus.PENDING;

    /** Optional note the learner attached to the request. */
    private String note;

    /** Admin decision fields. */
    private String decisionReason;
    private String decidedBy;
    private LocalDateTime decidedAt;

    private LocalDateTime createdAt = IndiaTime.now();
    private LocalDateTime updatedAt = IndiaTime.now();

    public enum RequestStatus {
        PENDING, APPROVED, DENIED
    }
}

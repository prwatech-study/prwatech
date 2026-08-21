package com.prwatech.skillama.model;

import com.prwatech.skillama.util.IndiaTime;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * A learner's request (from the Explore catalog) to be enrolled in a course.
 * Learners cannot self-enroll — an admin approves (creating the enrollment with
 * type REQUEST_APPROVED) or denies with a reason. One PENDING request per
 * (user, course) is enforced in the service.
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

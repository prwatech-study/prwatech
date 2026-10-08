package com.prwatech.skillama.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "ai_interview_schedules")
public class AiInterviewSchedule {
    @Id
    private String id;

    @Indexed
    private String organizationId;

    /** Lower-cased. One live schedule per email; reschedule retires the previous row. */
    @Indexed
    private String candidateEmail;

    private Instant scheduledAt;
    private Integer durationMinutes;
    private Integer joinGraceMinutes;

    /** BEHAVIORAL, TECHNICAL, or MIXED. */
    private String style;
    private String courseId;
    private String courseName;
    private String jdText;

    @Builder.Default
    private List<String> questionIds = new ArrayList<>();

    @Builder.Default
    private List<InterviewSeedQuestion> seedQuestions = new ArrayList<>();

    @Indexed(unique = true)
    private String inviteToken;

    /** SCHEDULED, JOINABLE, IN_PROGRESS, WINDING_DOWN, COMPLETED, EXPIRED, CANCELLED, RESCHEDULED. */
    @Indexed
    private String status;

    private String previousScheduleId;
    private String createdBy;
    private Instant createdAt;

    @Builder.Default
    private List<InterviewRescheduleEvent> rescheduleLog = new ArrayList<>();

    private Boolean emailSent;
}

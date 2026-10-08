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

/**
 * One live attempt per schedule. endsAt is the wall-clock slot end
 * (scheduledAt + duration) and does not move if the candidate disconnects.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "ai_interview_sessions")
public class AiInterviewSession {
    @Id
    private String id;

    @Indexed(unique = true)
    private String scheduleId;

    @Indexed
    private String organizationId;

    private String candidateEmail;

    @Indexed(unique = true)
    private String sessionToken;

    /** Tab that currently owns the single-session lock. */
    private String clientInstanceId;
    private Instant lastHeartbeatAt;

    /** IN_PROGRESS, WINDING_DOWN, or COMPLETED. */
    private String status;

    private Instant startedAt;
    /** Fixed at scheduledAt + durationMinutes. */
    private Instant endsAt;
    private Instant completedAt;

    @Builder.Default
    private List<InterviewTurn> turns = new ArrayList<>();

    @Builder.Default
    private List<InterviewSnapshot> snapshots = new ArrayList<>();

    /** Admin-only. Never copied onto candidate list or session responses. */
    private Integer score;
    private String feedback;
    private Double minutesSpent;
    private Double creditEstimate;
}

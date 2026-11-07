package com.prwatech.skillama.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "event_memberships")
public class EventMembership {

    public enum Status { ACTIVE, REVERTED }

    public enum RevertReason { CREDITS_EXHAUSTED, EXPIRED, ENDED_BY_ADMIN }

    @Id
    private String id;

    @Indexed
    private String eventId;

    @Indexed(unique = true)
    private String userId;

    private LocalDateTime joinedAt;

    /** Course that stays after the event ends. */
    private String retainedCourseId;

    private Status status;

    /** AI spend while this membership was covering the student. */
    @Builder.Default
    private double spendUsd = 0.0;

    private RevertReason revertReason;
    private LocalDateTime revertedAt;
}

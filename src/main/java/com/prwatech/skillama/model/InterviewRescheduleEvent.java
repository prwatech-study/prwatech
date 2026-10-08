package com.prwatech.skillama.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewRescheduleEvent {
    private Instant from;
    private Instant to;
    private String reason;
    private Instant at;
    private String actorId;
    private String previousScheduleId;
    private String newScheduleId;
}

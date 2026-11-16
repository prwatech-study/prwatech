package com.prwatech.skillama.model.outreach;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutreachWaveDelivery {
    public enum Status {
        IDLE,
        PENDING,
        RETRY_PENDING,
        SUBMITTED,
        FAILED_PERMANENT,
        SKIPPED
    }

    @Builder.Default
    private Status status = Status.IDLE;
    @Builder.Default
    private int attemptCount = 0;
    private LocalDateTime nextAttemptAt;
    private LocalDateTime lastAttemptAt;
    private LocalDateTime submittedAt;
    private String failureReason;
    private String lastError;
    @Builder.Default
    private boolean uniqueOpen = false;
    @Builder.Default
    private int openCount = 0;
    @Builder.Default
    private boolean uniqueClick = false;
    @Builder.Default
    private int clickCount = 0;
}
package com.prwatech.skillama.model.outreach;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "outreach_campaigns")
public class OutreachCampaign {

    public static final String SINGLETON_ID = "prwatech-legacy-v1";

    public enum Status {
        DRAFT, RUNNING, PAUSED, COMPLETED
    }

    @Id
    private String id;

    @Builder.Default
    private Status status = Status.DRAFT;

    @Builder.Default
    private int currentWave = 1;

    @Builder.Default
    private int maxWaves = 3;

    @Builder.Default
    private int dailyBatchSize = 500;

    @Builder.Default
    private double signupBonusUsd = 1.0;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime lastSyncAt;
    private LocalDateTime lastJobAt;

    private int lastJobSubmitted;
    private int lastJobFailed;
    private long lastJobDurationSec;

    private LocalDate submittedCountDate;
    @Builder.Default
    private int submittedToday = 0;

    @Builder.Default
    private boolean batchInProgress = false;
    private LocalDateTime batchStartedAt;

    private Long syncEligibleLegacy;
    private Long syncExcludedOnSkillama;
    private Long syncExcludedDisabled;
    private Long syncExcludedInvalid;
    private Long syncExcludedDuplicate;
    private Long syncQueuedNew;

    private Long wave2EligibleSnapshot;
    private Long wave3EligibleSnapshot;

    private LocalDateTime updatedAt;
}
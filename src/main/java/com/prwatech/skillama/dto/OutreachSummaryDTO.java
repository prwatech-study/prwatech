package com.prwatech.skillama.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Aggregate outreach metrics only — no recipient PII. */
@Data
@Builder
public class OutreachSummaryDTO {
    private String campaignStatus;
    private boolean outreachEnabled;
    private int currentWave;
    private int maxWaves;
    private int dailyCap;
    private int submittedToday;
    private int remainingTodayCapacity;

    private long queuedTotal;
    private long pendingInCurrentWave;
    private long retryPending;
    private long submitted;
    private long sendFailed;
    private long exhaustedRetries;
    private long suppressed;
    private long optedOut;
    private long registrationsAttributed;
    private long bonusGranted;
    private long uniqueOpens;
    private long uniqueClicks;
    private long unsubscribes;
    private long landings;

    private long wave1Submitted;
    private long wave2Submitted;
    private long wave3Submitted;
    private Long wave2EligibleSnapshot;
    private Long wave3EligibleSnapshot;

    private Long eligibleLegacyCount;
    private Long excludedAlreadyOnSkillama;
    private Long excludedDisabled;
    private Long excludedInvalidEmail;

    private int lastBatchSubmitted;
    private int lastBatchFailed;
    private long lastBatchDurationSec;
    private boolean batchInProgress;
    private LocalDateTime batchStartedAt;

    private Double openRate;
    private Double clickRate;
    private Double registrationRate;
    private Integer estimatedDaysRemainingCurrentWave;
    private Integer estimatedDaysRemainingCampaign;

    private Map<String, Long> failureReasonBuckets;
    private List<OutreachDailyRowDTO> dailySeries;
    private OutreachTemplatePreviewDTO templatePreview;
}
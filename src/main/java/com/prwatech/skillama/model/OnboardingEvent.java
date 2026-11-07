package com.prwatech.skillama.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "onboarding_events")
public class OnboardingEvent {

    public enum LimitMode { CREDIT, TIME }

    public enum Status { DRAFT, OPEN, CLOSED, ENDED }

    @Id
    private String id;

    private String name;
    private String organizerName;
    private String trainerName;

    /** The day the booth or session happens. Registration open time is separate. */
    private LocalDate eventDate;

    /** When the referral code starts accepting new students. */
    private LocalDateTime registrationOpensAt;

    private LimitMode limitMode;

    /** Display credits per student. Credit mode only. Immutable after create. */
    private Double creditsPerStudent;

    /** Shared access end. Time mode only. Immutable after create. */
    private LocalDateTime endsAt;

    @Builder.Default
    private List<String> featureCodes = new ArrayList<>();

    @Builder.Default
    private List<String> courseIds = new ArrayList<>();

    /** Optional alarm threshold in USD. Null means show burn and do not email. */
    private Double alarmBudgetUsd;

    @Builder.Default
    private double lifetimeSpendUsd = 0.0;

    /** Highest alarm percent already emailed (50, 80, 100, 125, ...). */
    private Integer lastSpendAlarmPercent;

    private LocalDateTime lastWeeklyAlarmAt;

    @Indexed(unique = true, sparse = true)
    private String referralCode;

    private Status status;

    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

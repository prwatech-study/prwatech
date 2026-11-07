package com.prwatech.skillama.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class OnboardingEventSummaryDTO {
    private String id;
    private String name;
    private String organizerName;
    private String trainerName;
    private LocalDate eventDate;
    private LocalDateTime registrationOpensAt;
    private String limitMode;
    private Double creditsPerStudent;
    private LocalDateTime endsAt;
    private List<String> featureCodes;
    private List<String> courseIds;
    private Double alarmBudgetCredits;
    private Double lifetimeCreditsBurned;
    private String referralCode;
    private String referralLink;
    private String status;
    private long studentsJoined;
    private long activeCount;
    private long revertedCount;
}

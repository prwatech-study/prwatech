package com.prwatech.skillama.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class UpsertOnboardingEventRequest {
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
    /** Alarm threshold in display credits. Blank means show burn and do not email. */
    private Double alarmBudgetCredits;
    private Boolean publish;
    /** Present only so a client that tries to change the code is rejected. */
    private String referralCode;
}

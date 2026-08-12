package com.prwatech.skillama.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/** Singleton platform flag: route Skillama AI calls to dev-ai when enabled. */
@Data
@Document(collection = "platform_ai_settings")
public class PlatformAiSettings {
    public static final String SINGLETON_ID = "PLATFORM_AI_SETTINGS";

    @Id
    private String id = SINGLETON_ID;
    private boolean devModeEnabled;
    private boolean aiUsageTrackingEnabled = true;
    private double platformMonthlyBudgetUsd = 1000.0;
    private double freemiumMonthlyBudgetUsdPerUser = 0.30;
    /** Owner-tunable referral reward (USD), read by FreemiumService#rewardReferrer. */
    private double referralRewardUsd = 0.20;
    /** Owner-tunable course-share reward (USD), read by CourseShareService#trackShare. */
    private double courseShareRewardUsd = 0.20;
    private LocalDateTime updatedAt;
    private String updatedBy;
}

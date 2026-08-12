package com.prwatech.skillama.model;

import lombok.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")
public class User {
    @Id
    private String id;
    private String name;
    private String email;
    /** Accept on login/register requests; never include in API responses. */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
    private boolean active;
    private String activationKey;
    private GenderEnum gender;

    @Indexed
    private UserRole role; // USER, ADMIN, OWNER, TESTER (defaults to USER)

    private PlanTier planTier;
    private String phone;
    /** Freemium course picked at signup; immutable once set. */
    private String chosenFreemiumCourseId;
    private Boolean emailVerified;
    @Indexed(unique = true, sparse = true)
    private String referralCode;
    private String referredBy;

    /** Accumulated Bedrock cost for the current calendar month (USD). */
    @Builder.Default
    private Double aiCostUsdThisPeriod = 0.0;
    /** Start of the current AI cost billing period (calendar month). */
    private LocalDateTime aiCostPeriodStart;

    /**
     * Subscription catalog code: SPARK, PULSE, NOVA, QUANTUM.
     * Null treated as Spark/freemium for learners without an active paid sub.
     */
    private String subscriptionPlanCode;
    /** ACTIVE, CANCELLED, EXPIRED — null for never-subscribed freemium users. */
    private String subscriptionStatus;
    private LocalDateTime currentPeriodEnd;
    /**
     * Paid-plan AI wallet ceiling in USD for the current period.
     * Null = use platform freemium budget (Spark) or unlimited (ENTERPRISE / legacy PAID).
     */
    private Double aiWalletLimitUsd;

    /**
     * Permanent referral reward (USD) added on top of the effective AI wallet base.
     * Earned by the REFERRER each time someone signs up with their code — stacks without limit.
     */
    @Builder.Default
    private Double referralBonusUsd = 0.0;

    /**
     * Generic reward-credits counter — earned via actions like sharing a course.
     * Kept as a raw display counter; shareBonusUsd below is the spendable equivalent.
     */
    @Builder.Default
    private Integer credits = 0;

    /**
     * Permanent course-share reward (USD) added on top of the effective AI wallet base.
     * Mirrors referralBonusUsd — earned by sharing a course to an eligible platform,
     * stacks without limit. See AiUsageService#resolveBudgetLimitUsd.
     */
    @Builder.Default
    private Double shareBonusUsd = 0.0;

    @Builder.Default
    private List<String> enabledModules = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastLoginAt;
    @Builder.Default
    private Integer loginCount = 0;
    private String createdBy;
    private String updatedBy;

    /**
     * Bumped on every login and logout; embedded in each minted JWT as the {@code tv} claim.
     * A request whose token carries an older version than this is a token from a session that
     * has since been replaced (login elsewhere) or logged out — used to enforce a single active
     * session per user and to make logout actually revoke server-side.
     */
    @Builder.Default
    private Integer tokenVersion = 0;

    /** True from login until logout; used only to decide whether a new login should conflict. */
    @Builder.Default
    private Boolean sessionActive = false;

    /** Learner LMS UI theme: classic | aurora */
    private String lmsThemePreference;

    /** OAuth provider subject IDs (sparse unique indexes). */
    @Indexed(unique = true, sparse = true)
    private String googleSub;
    @Indexed(unique = true, sparse = true)
    private String appleSub;
    private AuthProvider authProvider;
    private String profileImageUrl;
    @Builder.Default
    private Boolean onboardingCompleted = false;
    private LocalDateTime onboardingCompletedAt;

    /**
     * Per-module CRUD grants for ADMIN users. Null or empty = legacy full access (same as today).
     * OWNER ignores this field (always full access).
     */
    @Builder.Default
    private List<AdminModulePermission> adminModulePermissions = new ArrayList<>();

    public enum AuthProvider {
        EMAIL, GOOGLE, APPLE
    }

    public enum UserRole {
        USER, ADMIN, OWNER, TESTER
    }

    /** Null/missing role in Mongo is treated as learner (USER). */
    public UserRole getEffectiveRole() {
        return role != null ? role : UserRole.USER;
    }

    public enum PlanTier {
        FREEMIUM, PAID, ENTERPRISE
    }
}

package com.prwatech.skillama.dto;

import com.prwatech.skillama.model.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FreemiumStatusDTO {
    private User.PlanTier planTier;
    private String subscriptionPlanCode;
    private String subscriptionStatus;
    private String phone;
    private Boolean emailVerified;
    private String referralCode;
    private String referredBy;
    /** Permanent referrer reward (USD) stacked on top of this user's AI wallet base. */
    private Double referralBonusUsd;
    /** Number of successful referral signups this reward corresponds to — for the "My Referrals" view. */
    private Integer referralCount;
    /** Permanent course-share reward (USD) stacked on top of this user's AI wallet base. */
    private Double shareBonusUsd;
    /** Generic reward-credits balance (e.g. earned from course-share rewards); not USD. */
    private Integer credits;
    /**
     * True when this user's AI wallet is not metered at all — staff, ENTERPRISE, legacy
     * (planTier null), and legacy PAID accounts without a wallet. Mirrors the wallet
     * enforcement in AiUsageService; it is no longer derived from a query count.
     */
    private Boolean unlimitedQueries;
    private List<String> enabledModules;
    private AiBudgetDTO aiBudget;
}

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
    private Integer queryCreditsUsed;
    private Integer queryCreditsLimit;
    /** True for PAID, ENTERPRISE, and legacy (planTier null) accounts with unlimited AI queries. */
    private Boolean unlimitedQueries;
    private List<String> enabledModules;
    private AiBudgetDTO aiBudget;
}

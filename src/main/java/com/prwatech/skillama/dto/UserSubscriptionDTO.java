package com.prwatech.skillama.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.prwatech.skillama.model.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserSubscriptionDTO {
    private String subscriptionId;
    private String planCode;
    private String planDisplayName;
    private String status;
    private Boolean autoRenew;
    private LocalDateTime currentPeriodStart;
    private LocalDateTime currentPeriodEnd;
    private User.PlanTier planTier;
    private AiBudgetDTO aiBudget;
    private Double priceInr;
    private Double walletInr;
    private String provider;
}

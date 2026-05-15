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
    private String phone;
    private Boolean emailVerified;
    private String referralCode;
    private String referredBy;
    private Integer queryCreditsUsed;
    private Integer queryCreditsLimit;
    private List<String> enabledModules;
}

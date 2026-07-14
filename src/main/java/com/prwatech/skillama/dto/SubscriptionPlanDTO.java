package com.prwatech.skillama.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SubscriptionPlanDTO {
    private String code;
    private String displayName;
    private String description;
    private Double priceInr;
    private Double walletInr;
    private Integer queryCreditsLimit;
    private Boolean unlimitedQueries;
    private List<String> enabledModules;
    private Integer sortOrder;
    private User.PlanTier planTier;
}

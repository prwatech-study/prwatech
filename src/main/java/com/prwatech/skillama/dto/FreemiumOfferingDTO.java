package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Public freemium product definition (marketing + signup) — single source with FreemiumService. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FreemiumOfferingDTO {
    private List<String> baseModules;
    private List<String> modulesWithReferral;
    private String referralBonusModule;
    /** Permanent USD wallet reward the REFERRER earns per successful referral signup. */
    private double referrerRewardUsd;
    private boolean courseSelectionAtSignup;
}

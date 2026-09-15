package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Learner-facing "which module used my AI credits" breakdown for the current billing period.
 * Credits only — no USD, INR, tokens, call counts, or submodules.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiUsageModuleBreakdownDTO {
    private LocalDateTime periodStart;
    private double totalCredits;

    @Builder.Default
    private List<ModuleUsageDTO> byModule = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModuleUsageDTO {
        private String module;
        private double credits;
        private double percentOfTotal;
    }
}

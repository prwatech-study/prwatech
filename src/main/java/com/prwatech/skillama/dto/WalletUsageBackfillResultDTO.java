package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of the lapsed-wallet usage backfill (see AiUsageService.backfillLapsedWalletUsage):
 * per-user before/after of the aiCostUsdThisPeriod counter recomputed from ai_usage_events.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletUsageBackfillResultDTO {
    private boolean dryRun;
    private int candidatesScanned;
    private int updated;
    private int skippedActivePeriod;
    @Builder.Default
    private List<EntryDTO> entries = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EntryDTO {
        private String userId;
        private String email;
        private Double beforeUsd;
        private Double afterUsd;
    }
}

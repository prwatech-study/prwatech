package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Result of an admin AI-wallet adjustment (USD). Replaces the deleted query-credit
 * adjustment ledger — the durable audit trail lives in {@code AdminAuditLog}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletAdjustResultDTO {
    private String userId;
    private String userEmail;
    private String adminId;
    private String adminEmail;
    /** Requested change in USD (0 when an absolute newLimitUsd was supplied). */
    private Double deltaUsd;
    /** Wallet base before the adjustment (excludes the referral bonus). */
    private Double walletBeforeUsd;
    /** Wallet base after the adjustment (excludes the referral bonus). */
    private Double walletAfterUsd;
    /** Permanent referral reward, untouched by admin adjustments. */
    private Double referralBonusUsd;
    /** Permanent course-share reward, untouched by admin adjustments. */
    private Double shareBonusUsd;
    /** walletAfterUsd + referralBonusUsd + shareBonusUsd — what the budget check actually enforces. */
    private Double effectiveLimitUsd;
    private String reason;
    private LocalDateTime adjustedAt;
}

package com.prwatech.skillama.dto;

import lombok.Data;

/**
 * Admin AI-wallet adjustment request. Values are USD — the dollar wallet is the only
 * consumption limit (the legacy query-count allowance no longer exists).
 */
@Data
public class CreditAdjustRequestDTO {
    /** Positive tops the wallet up; negative reduces it (never below $0). */
    private Double deltaUsd;
    /** Optional: set an absolute wallet base in USD instead of applying a delta. */
    private Double newLimitUsd;
    private String reason;
}

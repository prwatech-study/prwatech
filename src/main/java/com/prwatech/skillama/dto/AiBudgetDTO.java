package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiBudgetDTO {
    private Double usedUsd;
    private Double limitUsd;
    private Double remainingUsd;
    private Double usedInr;
    private Double limitInr;
    private Double remainingInr;
    /** Permanent referral reward (USD) included in limitUsd — lets the UI show "base + bonus". */
    private Double referralBonusUsd;
    private Boolean unlimited;
    private Boolean limitReached;
}

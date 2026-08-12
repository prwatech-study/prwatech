package com.prwatech.skillama.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import com.prwatech.skillama.util.IndiaTime;

/**
 * One row per successful referral reward. Records the rate actually applied at the time,
 * so referralCount/history stay accurate even after an owner tunes referralRewardUsd later —
 * dividing the cumulative referralBonusUsd by the CURRENT rate would silently misreport past
 * referrals made at a different rate.
 */
@Data
@Document(collection = "referral_conversion_events")
public class ReferralConversionEvent {
    @Id
    private String id;
    private String referrerId;
    private String refereeId;
    private double rewardUsd;
    private LocalDateTime createdAt = IndiaTime.now();
}

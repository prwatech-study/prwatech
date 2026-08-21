package com.prwatech.skillama.model;

import com.prwatech.skillama.util.IndiaTime;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Audit row for every admin adjustment of a user's time-based wallet (B2B seats).
 * One row per adjustment — allocation history must stay reconstructable for
 * enterprise billing conversations.
 */
@Data
@Document(collection = "time_wallet_adjustment_events")
public class TimeWalletAdjustmentEvent {
    @Id
    private String id;

    @Indexed
    private String userId;

    private String adminId;

    /** Minutes added (positive) or removed (negative) from the allocation. */
    private double deltaMinutes;

    private double allocatedBeforeMinutes;
    private double allocatedAfterMinutes;

    private String reason;

    private LocalDateTime createdAt = IndiaTime.now();
}

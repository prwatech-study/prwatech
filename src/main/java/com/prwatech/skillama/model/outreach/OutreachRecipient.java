package com.prwatech.skillama.model.outreach;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "outreach_recipients")
public class OutreachRecipient {

    public enum Lifecycle {
        ACTIVE,
        OPTED_OUT,
        REGISTERED,
        SUPPRESSED
    }

    @Id
    private String id;

    @Indexed(unique = true)
    private String emailHash;

    /** Server-only; never returned on admin APIs. */
    private String email;

    private String legacyPrwatechUserId;
    /** First name for SMTP personalization only. */
    private String displayName;

    @Indexed(unique = true)
    private String trackingToken;

    @Builder.Default
    private Lifecycle lifecycle = Lifecycle.ACTIVE;
    private String suppressReason;

    @Builder.Default
    private Map<String, OutreachWaveDelivery> waves = new HashMap<>();

    @Builder.Default
    private int landingHits = 0;
    private LocalDateTime registeredAt;
    private String skillamaUserId;
    @Builder.Default
    private boolean bonusGranted = false;
    private LocalDateTime optedOutAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public OutreachWaveDelivery wave(int n) {
        String key = String.valueOf(n);
        return waves.computeIfAbsent(key, k -> OutreachWaveDelivery.builder().build());
    }
}
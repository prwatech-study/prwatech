package com.prwatech.skillama.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "user_subscriptions")
public class UserSubscription {
    @Id
    private String id;

    @Indexed
    private String userId;

    private String planCode;

    @Indexed
    private SubscriptionStatus status;

    private LocalDateTime currentPeriodStart;
    private LocalDateTime currentPeriodEnd;

    @Builder.Default
    private boolean autoRenew = true;

    /** MOCK until a real gateway is wired. */
    private String provider;

    private String providerSubscriptionId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime cancelledAt;

    public enum SubscriptionStatus {
        ACTIVE, CANCELLED, EXPIRED
    }
}

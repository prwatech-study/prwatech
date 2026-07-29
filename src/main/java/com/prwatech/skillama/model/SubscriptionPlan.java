package com.prwatech.skillama.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "subscription_plans")
public class SubscriptionPlan {
    @Id
    private String id;

    @Indexed(unique = true)
    private String code;

    private String displayName;
    private String description;

    /** Monthly price in INR (0 for Spark). */
    private double priceInr;

    /**
     * AI consumption wallet in INR for the billing period.
     * Null for Spark (uses platform freemium USD budget).
     */
    private Double walletInr;

    @Builder.Default
    private List<String> enabledModules = new ArrayList<>();

    private int sortOrder;

    @Builder.Default
    private boolean active = true;

    /** Maps to User.PlanTier.FREEMIUM for Spark; PAID for Pulse/Nova/Quantum. */
    private User.PlanTier planTier;
}

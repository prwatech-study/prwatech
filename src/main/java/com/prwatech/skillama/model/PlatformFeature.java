package com.prwatech.skillama.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "platform_features")
public class PlatformFeature {
    @Id
    private String id;

    @Indexed(unique = true)
    private String code;

    private String name;
    private String description;

    public enum FeatureCategory {
        LMS, AI, CORPORATE, BRANDING, SECURITY, LIMIT
    }

    public enum ValueType {
        BOOLEAN, INTEGER, JSON
    }

    /** Platform-wide student rollout. Org entitlements apply only after LIVE. */
    public enum RolloutStatus {
        HIDDEN, UPCOMING, LIVE
    }

    private FeatureCategory category;
    private ValueType valueType;
    @Builder.Default
    private boolean platformDefault = false;
    private Map<String, Object> defaultConfig;
    private int sortOrder;
    @Builder.Default
    private boolean active = true;
    @Builder.Default
    private List<String> dependsOn = new ArrayList<>();

    /** Null/missing treated as LIVE for backward compatibility until backfill runs. */
    private RolloutStatus rolloutStatus;

    /** Set when Owner transitions to LIVE; cleared when leaving LIVE. */
    private LocalDateTime liveAt;

    /** When true, feature appears in learner home/sidebar catalog. */
    @Builder.Default
    private boolean learnerVisible = false;

    public RolloutStatus getEffectiveRolloutStatus() {
        return rolloutStatus != null ? rolloutStatus : RolloutStatus.LIVE;
    }
}

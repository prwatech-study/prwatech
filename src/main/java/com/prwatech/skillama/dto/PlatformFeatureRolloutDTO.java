package com.prwatech.skillama.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.prwatech.skillama.model.PlatformFeature;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PlatformFeatureRolloutDTO {
    private String code;
    private String name;
    private String description;
    private PlatformFeature.FeatureCategory category;
    private PlatformFeature.RolloutStatus rolloutStatus;
    private LocalDateTime liveAt;
    private boolean learnerVisible;
    @JsonProperty("isNew")
    private boolean newBadge;
    private int sortOrder;
    private boolean active;
    /** Owner-facing pilot allowlist; omitted/empty on anonymous learner catalog. */
    private List<String> pilotUserIds;
}

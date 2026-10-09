package com.prwatech.skillama.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.prwatech.skillama.model.PlatformFeature;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

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
}

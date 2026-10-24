package com.prwatech.skillama.dto;

import com.prwatech.skillama.model.PlatformFeature;
import lombok.Data;

import java.util.List;

@Data
public class UpdateFeatureRolloutDTO {
    private PlatformFeature.RolloutStatus rolloutStatus;

    /**
     * When non-null, replaces the feature pilot allowlist (user ids).
     * Pass an empty list to clear pilots.
     */
    private List<String> pilotUserIds;
}

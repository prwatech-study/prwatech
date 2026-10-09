package com.prwatech.skillama.dto;

import com.prwatech.skillama.model.PlatformFeature;
import lombok.Data;

@Data
public class UpdateFeatureRolloutDTO {
    private PlatformFeature.RolloutStatus rolloutStatus;
}

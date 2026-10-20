package com.prwatech.skillama.dto;

import lombok.Data;

@Data
public class UpdateAiDevModeDTO {
    private Boolean devModeEnabled;
    /** Optional: enable/disable Hindi tutor for the platform. */
    private Boolean hindiTutorEnabled;
}

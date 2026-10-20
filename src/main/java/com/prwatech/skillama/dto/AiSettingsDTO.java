package com.prwatech.skillama.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AiSettingsDTO {
    private boolean devModeEnabled;
    /** Platform gate: learners may select Hindi tutor voice/UI when true. */
    private boolean hindiTutorEnabled;
    private LocalDateTime updatedAt;
}

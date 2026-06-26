package com.prwatech.skillama.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AiSettingsDTO {
    private boolean devModeEnabled;
    private LocalDateTime updatedAt;
}

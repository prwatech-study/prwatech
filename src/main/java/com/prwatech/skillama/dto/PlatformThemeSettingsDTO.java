package com.prwatech.skillama.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PlatformThemeSettingsDTO {
    private List<String> enabledThemes;
    private String defaultTheme;
    private LocalDateTime updatedAt;
}

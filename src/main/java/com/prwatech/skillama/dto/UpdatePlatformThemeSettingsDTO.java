package com.prwatech.skillama.dto;

import lombok.Data;

import java.util.List;

@Data
public class UpdatePlatformThemeSettingsDTO {
    private List<String> enabledThemes;
    private String defaultTheme;
}

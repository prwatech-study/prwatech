package com.prwatech.skillama.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Singleton: which built-in LMS themes the platform owner has allowed. */
@Data
@Document(collection = "platform_theme_settings")
public class PlatformThemeSettings {
    public static final String SINGLETON_ID = "PLATFORM_THEME_SETTINGS";
    public static final String CLASSIC = "classic";
    public static final String AURORA = "aurora";
    public static final String OBSIDIAN = "obsidian";
    public static final String DEFAULT_THEME = AURORA;
    public static final List<String> CATALOG = List.of(CLASSIC, AURORA, OBSIDIAN);

    @Id
    private String id = SINGLETON_ID;
    private List<String> enabledThemes = new ArrayList<>(CATALOG);
    private String defaultTheme = DEFAULT_THEME;
    private LocalDateTime updatedAt;
    private String updatedBy;
}

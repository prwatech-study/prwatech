package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.PlatformThemeSettingsDTO;
import com.prwatech.skillama.dto.UpdatePlatformThemeSettingsDTO;
import com.prwatech.skillama.model.PlatformThemeSettings;
import com.prwatech.skillama.repository.PlatformThemeSettingsRepository;
import com.prwatech.skillama.util.IndiaTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PlatformThemeSettingsService {

    private final PlatformThemeSettingsRepository repository;

    public PlatformThemeSettingsDTO getPublicSettings() {
        return toDto(loadOrDefault());
    }

    public PlatformThemeSettingsDTO updateSettings(UpdatePlatformThemeSettingsDTO body, String ownerUserId) {
        if (body == null || body.getEnabledThemes() == null) {
            throw new IllegalArgumentException("enabledThemes is required");
        }

        List<String> enabled = normalizeEnabled(body.getEnabledThemes());
        if (enabled.isEmpty()) {
            throw new IllegalArgumentException("at least one theme must stay enabled");
        }

        String defaultTheme = catalogId(body.getDefaultTheme());
        if (defaultTheme == null) {
            throw new IllegalArgumentException("defaultTheme must be classic, aurora, or obsidian");
        }
        if (!enabled.contains(defaultTheme)) {
            throw new IllegalArgumentException("defaultTheme must be one of the enabled themes");
        }

        PlatformThemeSettings settings = loadOrDefault();
        settings.setId(PlatformThemeSettings.SINGLETON_ID);
        settings.setEnabledThemes(enabled);
        settings.setDefaultTheme(defaultTheme);
        settings.setUpdatedAt(IndiaTime.now());
        settings.setUpdatedBy(ownerUserId);
        return toDto(repository.save(settings));
    }

    public boolean isThemeEnabled(String theme) {
        String id = catalogId(theme);
        return id != null && enabledThemes(loadOrDefault()).contains(id);
    }

    public String resolveEnabledTheme(String raw) {
        return resolveEnabledTheme(raw, loadOrDefault());
    }

    private String resolveEnabledTheme(String raw, PlatformThemeSettings settings) {
        List<String> enabled = enabledThemes(settings);
        String id = catalogId(raw);
        if (id != null && enabled.contains(id)) {
            return id;
        }
        String fallback = catalogId(settings.getDefaultTheme());
        if (fallback != null && enabled.contains(fallback)) {
            return fallback;
        }
        return enabled.get(0);
    }

    private PlatformThemeSettingsDTO toDto(PlatformThemeSettings settings) {
        return PlatformThemeSettingsDTO.builder()
                .enabledThemes(enabledThemes(settings))
                .defaultTheme(resolveEnabledTheme(settings.getDefaultTheme(), settings))
                .updatedAt(settings.getUpdatedAt())
                .build();
    }

    private PlatformThemeSettings loadOrDefault() {
        return repository.findById(PlatformThemeSettings.SINGLETON_ID)
                .orElseGet(() -> {
                    PlatformThemeSettings defaults = new PlatformThemeSettings();
                    defaults.setId(PlatformThemeSettings.SINGLETON_ID);
                    defaults.setEnabledThemes(new ArrayList<>(PlatformThemeSettings.CATALOG));
                    defaults.setDefaultTheme(PlatformThemeSettings.DEFAULT_THEME);
                    return defaults;
                });
    }

    private List<String> enabledThemes(PlatformThemeSettings settings) {
        List<String> stored = settings.getEnabledThemes();
        if (stored == null || stored.isEmpty()) {
            return new ArrayList<>(PlatformThemeSettings.CATALOG);
        }
        List<String> enabled = new ArrayList<>();
        for (String id : PlatformThemeSettings.CATALOG) {
            if (stored.stream().anyMatch(s -> id.equals(catalogId(s)))) {
                enabled.add(id);
            }
        }
        return enabled.isEmpty() ? new ArrayList<>(PlatformThemeSettings.CATALOG) : enabled;
    }

    private List<String> normalizeEnabled(List<String> raw) {
        Set<String> requested = new LinkedHashSet<>();
        for (String value : raw) {
            String id = catalogId(value);
            if (id == null) {
                throw new IllegalArgumentException("theme must be classic, aurora, or obsidian");
            }
            requested.add(id);
        }
        List<String> enabled = new ArrayList<>();
        for (String id : PlatformThemeSettings.CATALOG) {
            if (requested.contains(id)) {
                enabled.add(id);
            }
        }
        return enabled;
    }

    static String catalogId(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim().toLowerCase();
        return PlatformThemeSettings.CATALOG.contains(t) ? t : null;
    }
}

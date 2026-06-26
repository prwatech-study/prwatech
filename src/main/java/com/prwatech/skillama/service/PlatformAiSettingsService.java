package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.AiSettingsDTO;
import com.prwatech.skillama.dto.UpdateAiDevModeDTO;
import com.prwatech.skillama.model.PlatformAiSettings;
import com.prwatech.skillama.repository.PlatformAiSettingsRepository;
import com.prwatech.skillama.util.IndiaTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlatformAiSettingsService {

    private final PlatformAiSettingsRepository repository;

    public AiSettingsDTO getPublicSettings() {
        return toDto(loadOrDefault());
    }

    public AiSettingsDTO updateDevMode(UpdateAiDevModeDTO body, String ownerUserId) {
        if (body == null || body.getDevModeEnabled() == null) {
            throw new IllegalArgumentException("devModeEnabled is required");
        }

        PlatformAiSettings settings = loadOrDefault();
        settings.setId(PlatformAiSettings.SINGLETON_ID);
        settings.setDevModeEnabled(body.getDevModeEnabled());
        settings.setUpdatedAt(IndiaTime.now());
        settings.setUpdatedBy(ownerUserId);
        return toDto(repository.save(settings));
    }

    private PlatformAiSettings loadOrDefault() {
        return repository.findById(PlatformAiSettings.SINGLETON_ID)
                .orElseGet(() -> {
                    PlatformAiSettings defaults = new PlatformAiSettings();
                    defaults.setId(PlatformAiSettings.SINGLETON_ID);
                    defaults.setDevModeEnabled(false);
                    return defaults;
                });
    }

    private AiSettingsDTO toDto(PlatformAiSettings settings) {
        return AiSettingsDTO.builder()
                .devModeEnabled(settings.isDevModeEnabled())
                .updatedAt(settings.getUpdatedAt())
                .build();
    }
}

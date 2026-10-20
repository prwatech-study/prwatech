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
    private final AdminAuditService adminAuditService;

    public AiSettingsDTO getPublicSettings() {
        return toDto(loadOrDefault());
    }

    public AiSettingsDTO updateDevMode(UpdateAiDevModeDTO body, String ownerUserId) {
        if (body == null || (body.getDevModeEnabled() == null && body.getHindiTutorEnabled() == null)) {
            throw new IllegalArgumentException("devModeEnabled or hindiTutorEnabled is required");
        }

        PlatformAiSettings settings = loadOrDefault();
        settings.setId(PlatformAiSettings.SINGLETON_ID);
        if (body.getDevModeEnabled() != null) {
            settings.setDevModeEnabled(body.getDevModeEnabled());
        }
        if (body.getHindiTutorEnabled() != null) {
            settings.setHindiTutorEnabled(body.getHindiTutorEnabled());
            adminAuditService.log(
                    ownerUserId,
                    AdminAuditService.PLATFORM_HINDI_TUTOR_UPDATE,
                    "PlatformAiSettings",
                    PlatformAiSettings.SINGLETON_ID,
                    "Hindi tutor enabled=" + body.getHindiTutorEnabled(),
                    null);
        }
        settings.setUpdatedAt(IndiaTime.now());
        settings.setUpdatedBy(ownerUserId);
        return toDto(repository.save(settings));
    }

    public boolean isHindiTutorEnabled() {
        return loadOrDefault().isHindiTutorEnabled();
    }

    private PlatformAiSettings loadOrDefault() {
        return repository.findById(PlatformAiSettings.SINGLETON_ID)
                .orElseGet(() -> {
                    PlatformAiSettings defaults = new PlatformAiSettings();
                    defaults.setId(PlatformAiSettings.SINGLETON_ID);
                    defaults.setDevModeEnabled(false);
                    defaults.setHindiTutorEnabled(false);
                    return defaults;
                });
    }

    private AiSettingsDTO toDto(PlatformAiSettings settings) {
        return AiSettingsDTO.builder()
                .devModeEnabled(settings.isDevModeEnabled())
                .hindiTutorEnabled(settings.isHindiTutorEnabled())
                .updatedAt(settings.getUpdatedAt())
                .build();
    }
}

package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.PlatformThemeSettingsDTO;
import com.prwatech.skillama.dto.UpdatePlatformThemeSettingsDTO;
import com.prwatech.skillama.model.PlatformThemeSettings;
import com.prwatech.skillama.repository.PlatformThemeSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlatformThemeSettingsServiceTest {

    @Mock private PlatformThemeSettingsRepository repository;

    private PlatformThemeSettingsService service;

    @BeforeEach
    void setUp() {
        service = new PlatformThemeSettingsService(repository);
        when(repository.save(any(PlatformThemeSettings.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void publicSettingsDefaultToFullCatalog() {
        when(repository.findById(PlatformThemeSettings.SINGLETON_ID)).thenReturn(Optional.empty());
        PlatformThemeSettingsDTO dto = service.getPublicSettings();
        assertEquals(List.of("classic", "aurora", "obsidian"), dto.getEnabledThemes());
        assertEquals("aurora", dto.getDefaultTheme());
    }

    @Test
    void updateRejectsEmptyAllowList() {
        UpdatePlatformThemeSettingsDTO body = new UpdatePlatformThemeSettingsDTO();
        body.setEnabledThemes(List.of());
        body.setDefaultTheme("aurora");
        assertThrows(IllegalArgumentException.class, () -> service.updateSettings(body, "owner"));
    }

    @Test
    void updateRejectsUnknownThemeId() {
        UpdatePlatformThemeSettingsDTO body = new UpdatePlatformThemeSettingsDTO();
        body.setEnabledThemes(List.of("classic", "neon"));
        body.setDefaultTheme("classic");
        assertThrows(IllegalArgumentException.class, () -> service.updateSettings(body, "owner"));
    }

    @Test
    void updateRejectsDefaultOutsideAllowList() {
        UpdatePlatformThemeSettingsDTO body = new UpdatePlatformThemeSettingsDTO();
        body.setEnabledThemes(List.of("classic"));
        body.setDefaultTheme("obsidian");
        assertThrows(IllegalArgumentException.class, () -> service.updateSettings(body, "owner"));
    }

    @Test
    void updatePersistsEnabledThemesAndDefault() {
        when(repository.findById(PlatformThemeSettings.SINGLETON_ID)).thenReturn(Optional.empty());
        UpdatePlatformThemeSettingsDTO body = new UpdatePlatformThemeSettingsDTO();
        body.setEnabledThemes(List.of("obsidian", "classic"));
        body.setDefaultTheme("classic");

        PlatformThemeSettingsDTO dto = service.updateSettings(body, "owner1");

        assertEquals(List.of("classic", "obsidian"), dto.getEnabledThemes());
        assertEquals("classic", dto.getDefaultTheme());
        ArgumentCaptor<PlatformThemeSettings> captor = ArgumentCaptor.forClass(PlatformThemeSettings.class);
        verify(repository).save(captor.capture());
        assertEquals("owner1", captor.getValue().getUpdatedBy());
        assertEquals(PlatformThemeSettings.SINGLETON_ID, captor.getValue().getId());
    }

    @Test
    void isThemeEnabledRespectsStoredAllowList() {
        PlatformThemeSettings stored = new PlatformThemeSettings();
        stored.setEnabledThemes(List.of("classic", "aurora"));
        stored.setDefaultTheme("aurora");
        when(repository.findById(PlatformThemeSettings.SINGLETON_ID)).thenReturn(Optional.of(stored));

        assertTrue(service.isThemeEnabled("classic"));
        assertTrue(service.isThemeEnabled("aurora"));
        assertFalse(service.isThemeEnabled("obsidian"));
        assertEquals("aurora", service.resolveEnabledTheme("obsidian"));
        assertEquals("classic", service.resolveEnabledTheme("classic"));
    }
}

package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.AiSettingsDTO;
import com.prwatech.skillama.dto.UpdateAiDevModeDTO;
import com.prwatech.skillama.model.PlatformAiSettings;
import com.prwatech.skillama.repository.PlatformAiSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlatformAiSettingsServiceTest {

    @Mock private PlatformAiSettingsRepository repository;

    private PlatformAiSettingsService service;

    @BeforeEach
    void setUp() {
        service = new PlatformAiSettingsService(repository);
    }

    @Test
    void publicSettingsDefaultsToDevModeOffWhenNoneStored() {
        when(repository.findById(PlatformAiSettings.SINGLETON_ID)).thenReturn(Optional.empty());
        AiSettingsDTO dto = service.getPublicSettings();
        assertFalse(dto.isDevModeEnabled());
    }

    @Test
    void publicSettingsReflectsStoredValue() {
        PlatformAiSettings stored = new PlatformAiSettings();
        stored.setDevModeEnabled(true);
        when(repository.findById(PlatformAiSettings.SINGLETON_ID)).thenReturn(Optional.of(stored));
        assertTrue(service.getPublicSettings().isDevModeEnabled());
    }

    @Test
    void updateDevModeRejectsNullBodyOrFlag() {
        assertThrows(IllegalArgumentException.class, () -> service.updateDevMode(null, "owner"));
        assertThrows(IllegalArgumentException.class,
                () -> service.updateDevMode(new UpdateAiDevModeDTO(), "owner"));
    }

    @Test
    void updateDevModePersistsFlagAndStamps() {
        when(repository.findById(PlatformAiSettings.SINGLETON_ID)).thenReturn(Optional.empty());
        when(repository.save(any(PlatformAiSettings.class))).thenAnswer(inv -> inv.getArgument(0));
        UpdateAiDevModeDTO body = new UpdateAiDevModeDTO();
        body.setDevModeEnabled(true);

        AiSettingsDTO dto = service.updateDevMode(body, "owner1");

        assertTrue(dto.isDevModeEnabled());
        ArgumentCaptor<PlatformAiSettings> captor = ArgumentCaptor.forClass(PlatformAiSettings.class);
        verify(repository).save(captor.capture());
        assertTrue(captor.getValue().isDevModeEnabled());
        org.junit.jupiter.api.Assertions.assertEquals("owner1", captor.getValue().getUpdatedBy());
        org.junit.jupiter.api.Assertions.assertEquals(PlatformAiSettings.SINGLETON_ID, captor.getValue().getId());
    }
}

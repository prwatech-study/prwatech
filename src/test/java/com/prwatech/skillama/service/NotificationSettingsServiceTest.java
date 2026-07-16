package com.prwatech.skillama.service;

import com.prwatech.common.dto.EmailSendDto;
import com.prwatech.common.service.impl.EmailServiceImpl;
import com.prwatech.skillama.dto.UpdateNotificationSettingsDTO;
import com.prwatech.skillama.model.PlatformNotificationSettings;
import com.prwatech.skillama.notification.NotificationEventType;
import com.prwatech.skillama.repository.PlatformNotificationSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationSettingsServiceTest {

    @Mock private PlatformNotificationSettingsRepository settingsRepository;
    @Mock private EmailServiceImpl emailService;

    private NotificationSettingsService service;

    @BeforeEach
    void setUp() {
        service = new NotificationSettingsService(settingsRepository, emailService);
    }

    private PlatformNotificationSettings settingsWith(Map<String, Boolean> enabled,
                                                      Map<String, List<String>> recipients) {
        PlatformNotificationSettings s = new PlatformNotificationSettings();
        if (enabled != null) s.setTypeEnabled(enabled);
        if (recipients != null) s.setTypeRecipientEmails(recipients);
        return s;
    }

    @Test
    void isEnabledDefaultsTrueWithoutSettings() {
        when(settingsRepository.findById(PlatformNotificationSettings.SINGLETON_ID)).thenReturn(Optional.empty());
        assertTrue(service.isEnabled(NotificationEventType.FEEDBACK_NEW));
    }

    @Test
    void isEnabledHonoursDisabledFlag() {
        when(settingsRepository.findById(PlatformNotificationSettings.SINGLETON_ID))
                .thenReturn(Optional.of(settingsWith(
                        Map.of(NotificationEventType.FEEDBACK_NEW.name(), false), null)));
        assertFalse(service.isEnabled(NotificationEventType.FEEDBACK_NEW));
    }

    @Test
    void sendTeamNotificationRejectsLearnerType() {
        assertThrows(IllegalArgumentException.class, () ->
                service.sendTeamNotification(NotificationEventType.FEEDBACK_REPLY, "s", "b"));
    }

    @Test
    void sendTeamNotificationSkipsWhenNoRecipients() {
        when(settingsRepository.findById(PlatformNotificationSettings.SINGLETON_ID))
                .thenReturn(Optional.of(settingsWith(null, Map.of())));
        service.sendTeamNotification(NotificationEventType.FEEDBACK_NEW, "s", "b");
        verify(emailService, never()).sendEmail(any());
    }

    @Test
    void sendTeamNotificationSkipsWhenDisabled() {
        when(settingsRepository.findById(PlatformNotificationSettings.SINGLETON_ID))
                .thenReturn(Optional.of(settingsWith(
                        Map.of(NotificationEventType.FEEDBACK_NEW.name(), false),
                        Map.of(NotificationEventType.FEEDBACK_NEW.name(), List.of("team@x.com")))));
        service.sendTeamNotification(NotificationEventType.FEEDBACK_NEW, "s", "b");
        verify(emailService, never()).sendEmail(any());
    }

    @Test
    void sendTeamNotificationEmailsEachRecipientAndSwallowsErrors() {
        when(settingsRepository.findById(PlatformNotificationSettings.SINGLETON_ID))
                .thenReturn(Optional.of(settingsWith(null,
                        Map.of(NotificationEventType.FEEDBACK_NEW.name(),
                                List.of("a@x.com", "b@x.com")))));
        doThrow(new RuntimeException("smtp")).when(emailService).sendEmail(any(EmailSendDto.class));

        service.sendTeamNotification(NotificationEventType.FEEDBACK_NEW, "s", "b"); // must not throw
        verify(emailService, times(2)).sendEmail(any(EmailSendDto.class));
    }

    @Test
    void sendLearnerNotificationRejectsTeamType() {
        assertThrows(IllegalArgumentException.class, () ->
                service.sendLearnerNotification(NotificationEventType.FEEDBACK_NEW, "u@x.com", "s", "b"));
    }

    @Test
    void sendLearnerNotificationSkipsBlankEmail() {
        when(settingsRepository.findById(PlatformNotificationSettings.SINGLETON_ID)).thenReturn(Optional.empty());
        service.sendLearnerNotification(NotificationEventType.FEEDBACK_REPLY, "  ", "s", "b");
        verify(emailService, never()).sendEmail(any());
    }

    @Test
    void sendLearnerNotificationSendsToLearner() {
        when(settingsRepository.findById(PlatformNotificationSettings.SINGLETON_ID)).thenReturn(Optional.empty());
        service.sendLearnerNotification(NotificationEventType.FEEDBACK_REPLY, "u@x.com", "s", "b");
        verify(emailService).sendEmail(any(EmailSendDto.class));
    }

    @Test
    void updateSettingsRejectsNullBody() {
        assertThrows(IllegalArgumentException.class, () -> service.updateSettings(null, "admin"));
    }

    @Test
    void updateSettingsIgnoresInvalidTypeIdsAndNormalizesEmails() {
        when(settingsRepository.findById(PlatformNotificationSettings.SINGLETON_ID)).thenReturn(Optional.empty());
        when(settingsRepository.save(any(PlatformNotificationSettings.class))).thenAnswer(inv -> {
            // capture the saved settings; getAdminSettings re-reads via findById
            when(settingsRepository.findById(PlatformNotificationSettings.SINGLETON_ID))
                    .thenReturn(Optional.of(inv.getArgument(0)));
            return inv.getArgument(0);
        });

        UpdateNotificationSettingsDTO body = new UpdateNotificationSettingsDTO();
        body.setTypeEnabled(Map.of(
                NotificationEventType.FEEDBACK_NEW.name(), false,
                "NOT_A_TYPE", true));
        body.setTypeRecipientEmails(Map.of(
                NotificationEventType.FEEDBACK_NEW.name(), List.of("Team@X.com", "team@x.com", "  ")));

        service.updateSettings(body, "admin");

        // FEEDBACK_NEW now disabled and recipient list normalized/deduped
        assertFalse(service.isEnabled(NotificationEventType.FEEDBACK_NEW));
        assertTrue(service.getRecipientEmailsForType(NotificationEventType.FEEDBACK_NEW)
                .equals(List.of("team@x.com")));
    }
}

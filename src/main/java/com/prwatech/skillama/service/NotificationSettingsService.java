package com.prwatech.skillama.service;

import com.prwatech.common.dto.EmailSendDto;
import com.prwatech.common.service.impl.EmailServiceImpl;
import com.prwatech.skillama.dto.NotificationSettingsDTO;
import com.prwatech.skillama.dto.NotificationTypeSettingDTO;
import com.prwatech.skillama.dto.UpdateNotificationSettingsDTO;
import com.prwatech.skillama.model.PlatformNotificationSettings;
import com.prwatech.skillama.notification.NotificationEventType;
import com.prwatech.skillama.repository.PlatformNotificationSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationSettingsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationSettingsService.class);

    private final PlatformNotificationSettingsRepository settingsRepository;
    private final EmailServiceImpl emailService;

    public NotificationSettingsDTO getAdminSettings() {
        PlatformNotificationSettings persisted = loadPersisted();
        final PlatformNotificationSettings settings =
                persisted != null ? persisted : new PlatformNotificationSettings();
        List<NotificationTypeSettingDTO> types = Arrays.stream(NotificationEventType.values())
                .map(type -> toTypeDto(type, settings))
                .collect(Collectors.toList());
        return NotificationSettingsDTO.builder()
                .notificationTypes(types)
                .updatedAt(settings.getUpdatedAt())
                .build();
    }

    public NotificationSettingsDTO updateSettings(UpdateNotificationSettingsDTO body, String adminUserId) {
        if (body == null) {
            throw new IllegalArgumentException("Request body is required");
        }

        PlatformNotificationSettings settings = loadPersisted();
        if (settings == null) {
            settings = new PlatformNotificationSettings();
            settings.setId(PlatformNotificationSettings.SINGLETON_ID);
        }

        if (body.getTypeRecipientEmails() != null) {
            Map<String, List<String>> merged = settings.getTypeRecipientEmails() != null
                    ? new LinkedHashMap<>(settings.getTypeRecipientEmails())
                    : new LinkedHashMap<>();
            body.getTypeRecipientEmails().forEach((key, emails) -> {
                if (isValidTypeId(key)) {
                    merged.put(key, new ArrayList<>(normalizeEmails(emails)));
                }
            });
            settings.setTypeRecipientEmails(merged);
        }

        if (body.getTypeEnabled() != null) {
            Map<String, Boolean> merged = settings.getTypeEnabled() != null
                    ? new LinkedHashMap<>(settings.getTypeEnabled())
                    : new LinkedHashMap<>();
            body.getTypeEnabled().forEach((key, value) -> {
                if (value != null && isValidTypeId(key)) {
                    merged.put(key, value);
                }
            });
            settings.setTypeEnabled(merged);
        }

        settings.setUpdatedAt(LocalDateTime.now());
        settings.setUpdatedBy(adminUserId);
        settingsRepository.save(settings);
        return getAdminSettings();
    }

    public boolean isEnabled(NotificationEventType type) {
        PlatformNotificationSettings settings = loadPersisted();
        if (settings == null || settings.getTypeEnabled() == null) {
            return true;
        }
        Boolean flag = settings.getTypeEnabled().get(type.name());
        return flag == null || flag;
    }

    public List<String> getRecipientEmailsForType(NotificationEventType type) {
        PlatformNotificationSettings settings = loadPersisted();
        if (settings == null) {
            return List.of();
        }
        return resolveRecipientsForType(type, settings);
    }

    public void sendTeamNotification(NotificationEventType type, String subject, String body) {
        if (type.getAudience() != NotificationEventType.NotificationAudience.TEAM) {
            throw new IllegalArgumentException("Not a team notification type: " + type);
        }
        if (!isEnabled(type)) {
            LOGGER.debug("Skipping disabled team notification: {}", type);
            return;
        }
        List<String> recipients = getRecipientEmailsForType(type);
        if (recipients.isEmpty()) {
            LOGGER.warn(
                    "Skipping team notification {} — no recipient emails configured for this notification type",
                    type);
            return;
        }
        for (String email : recipients) {
            try {
                emailService.sendEmail(new EmailSendDto(email, subject, body));
            } catch (Exception e) {
                LOGGER.warn("Failed to send {} notification to {}: {}", type, email, e.getMessage());
            }
        }
    }

    public void sendLearnerNotification(
            NotificationEventType type, String learnerEmail, String subject, String body) {
        if (type.getAudience() != NotificationEventType.NotificationAudience.LEARNER) {
            throw new IllegalArgumentException("Not a learner notification type: " + type);
        }
        if (!isEnabled(type)) {
            LOGGER.debug("Skipping disabled learner notification: {}", type);
            return;
        }
        if (!StringUtils.hasText(learnerEmail)) {
            LOGGER.warn("Skipping {} — no learner email", type);
            return;
        }
        try {
            emailService.sendEmail(new EmailSendDto(learnerEmail.trim(), subject, body));
        } catch (Exception e) {
            LOGGER.warn("Failed to send {} to {}: {}", type, learnerEmail, e.getMessage());
        }
    }

    private PlatformNotificationSettings loadPersisted() {
        return settingsRepository.findById(PlatformNotificationSettings.SINGLETON_ID).orElse(null);
    }

    private List<String> resolveRecipientsForType(
            NotificationEventType type, PlatformNotificationSettings settings) {
        if (settings.getTypeRecipientEmails() == null) {
            return List.of();
        }
        return normalizeEmails(settings.getTypeRecipientEmails().get(type.name()));
    }

    private NotificationTypeSettingDTO toTypeDto(NotificationEventType type, PlatformNotificationSettings settings) {
        boolean enabled = isEnabled(type, settings);
        boolean isTeam = type.getAudience() == NotificationEventType.NotificationAudience.TEAM;
        List<String> emails = isTeam ? resolveRecipientsForType(type, settings) : List.of();
        String recipientSummary = isTeam
                ? formatEmailSummary(emails)
                : "Learner's registered account email (per event)";
        return NotificationTypeSettingDTO.builder()
                .id(type.name())
                .label(type.getLabel())
                .description(type.getDescription())
                .audience(type.getAudience().name())
                .category(type.getCategory().name())
                .categoryLabel(type.getCategory().getLabel())
                .enabled(enabled)
                .recipientSummary(recipientSummary)
                .recipientEmails(emails)
                .build();
    }

    private boolean isEnabled(NotificationEventType type, PlatformNotificationSettings settings) {
        if (settings.getTypeEnabled() == null) {
            return true;
        }
        Boolean flag = settings.getTypeEnabled().get(type.name());
        return flag == null || flag;
    }

    private static String formatEmailSummary(List<String> emails) {
        if (emails.isEmpty()) {
            return "No emails configured for this notification";
        }
        return String.join(", ", emails);
    }

    private static List<String> normalizeEmails(List<String> raw) {
        if (raw == null) {
            return List.of();
        }
        return raw.stream()
                .filter(StringUtils::hasText)
                .map(e -> e.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .collect(Collectors.toList());
    }

    private static boolean isValidTypeId(String id) {
        try {
            NotificationEventType.valueOf(id);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}

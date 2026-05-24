package com.prwatech.skillama.service;

import com.prwatech.common.dto.EmailSendDto;
import com.prwatech.common.service.impl.EmailServiceImpl;
import com.prwatech.skillama.SkillamaNotificationEmails;
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
        PlatformNotificationSettings settings = loadOrDefault();
        List<String> teamEmails = normalizeTeamEmails(settings.getTeamRecipientEmails());
        List<NotificationTypeSettingDTO> types = Arrays.stream(NotificationEventType.values())
                .map(type -> toTypeDto(type, settings, teamEmails))
                .collect(Collectors.toList());
        return NotificationSettingsDTO.builder()
                .teamRecipientEmails(teamEmails)
                .notificationTypes(types)
                .updatedAt(settings.getUpdatedAt())
                .build();
    }

    public NotificationSettingsDTO updateSettings(UpdateNotificationSettingsDTO body, String adminUserId) {
        if (body == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        List<String> teamEmails = normalizeTeamEmails(body.getTeamRecipientEmails());
        if (teamEmails.isEmpty()) {
            throw new IllegalArgumentException("At least one team recipient email is required");
        }

        PlatformNotificationSettings settings = loadOrDefault();
        settings.setId(PlatformNotificationSettings.SINGLETON_ID);
        settings.setTeamRecipientEmails(teamEmails);
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
        PlatformNotificationSettings settings = loadOrDefault();
        if (settings.getTypeEnabled() == null) {
            return true;
        }
        Boolean flag = settings.getTypeEnabled().get(type.name());
        return flag == null || flag;
    }

    public List<String> getTeamRecipientEmails() {
        return normalizeTeamEmails(loadOrDefault().getTeamRecipientEmails());
    }

    public void sendTeamNotification(NotificationEventType type, String subject, String body) {
        if (type.getAudience() != NotificationEventType.NotificationAudience.TEAM) {
            throw new IllegalArgumentException("Not a team notification type: " + type);
        }
        if (!isEnabled(type)) {
            LOGGER.debug("Skipping disabled team notification: {}", type);
            return;
        }
        for (String email : getTeamRecipientEmails()) {
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

    private PlatformNotificationSettings loadOrDefault() {
        return settingsRepository.findById(PlatformNotificationSettings.SINGLETON_ID)
                .orElseGet(this::defaultSettings);
    }

    private PlatformNotificationSettings defaultSettings() {
        PlatformNotificationSettings settings = new PlatformNotificationSettings();
        settings.setId(PlatformNotificationSettings.SINGLETON_ID);
        settings.setTeamRecipientEmails(new ArrayList<>(SkillamaNotificationEmails.DEFAULT_TEAM_INBOXES));
        settings.setTypeEnabled(new LinkedHashMap<>());
        return settings;
    }

    private NotificationTypeSettingDTO toTypeDto(
            NotificationEventType type,
            PlatformNotificationSettings settings,
            List<String> teamEmails) {
        boolean enabled = isEnabled(type, settings);
        boolean isTeam = type.getAudience() == NotificationEventType.NotificationAudience.TEAM;
        String recipientSummary = isTeam
                ? formatTeamSummary(teamEmails)
                : "Learner's registered account email (per event)";
        return NotificationTypeSettingDTO.builder()
                .id(type.name())
                .label(type.getLabel())
                .description(type.getDescription())
                .audience(type.getAudience().name())
                .enabled(enabled)
                .recipientSummary(recipientSummary)
                .recipientEmails(isTeam ? teamEmails : List.of())
                .build();
    }

    private boolean isEnabled(NotificationEventType type, PlatformNotificationSettings settings) {
        if (settings.getTypeEnabled() == null) {
            return true;
        }
        Boolean flag = settings.getTypeEnabled().get(type.name());
        return flag == null || flag;
    }

    private static String formatTeamSummary(List<String> emails) {
        if (emails.isEmpty()) {
            return "No team emails configured";
        }
        return emails.size() + " team inbox" + (emails.size() == 1 ? "" : "es") + ": "
                + String.join(", ", emails);
    }

    private static List<String> normalizeTeamEmails(List<String> raw) {
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

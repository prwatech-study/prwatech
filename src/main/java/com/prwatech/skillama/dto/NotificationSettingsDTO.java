package com.prwatech.skillama.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class NotificationSettingsDTO {
    private List<String> teamRecipientEmails;
    private List<NotificationTypeSettingDTO> notificationTypes;
    private LocalDateTime updatedAt;
}

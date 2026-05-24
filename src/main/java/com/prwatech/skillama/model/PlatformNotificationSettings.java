package com.prwatech.skillama.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Document(collection = "platform_notification_settings")
public class PlatformNotificationSettings {

    public static final String SINGLETON_ID = "PLATFORM_NOTIFICATION_SETTINGS";

    @Id
    private String id = SINGLETON_ID;

    /** Team inboxes for all TEAM-audience notifications. */
    private List<String> teamRecipientEmails;

    /** Per-event on/off; key = {@link com.prwatech.skillama.notification.NotificationEventType#name()}. */
    private Map<String, Boolean> typeEnabled = new HashMap<>();

    private LocalDateTime updatedAt;
    private String updatedBy;
}

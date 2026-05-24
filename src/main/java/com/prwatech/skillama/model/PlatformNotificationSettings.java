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

    /**
     * @deprecated Legacy global list; migrated into {@link #typeRecipientEmails} on read.
     */
    @Deprecated
    private List<String> teamRecipientEmails;

    /** Per notification type; key = {@link com.prwatech.skillama.notification.NotificationEventType#name()}. */
    private Map<String, List<String>> typeRecipientEmails = new HashMap<>();

    /** Per-event on/off; key = {@link com.prwatech.skillama.notification.NotificationEventType#name()}. */
    private Map<String, Boolean> typeEnabled = new HashMap<>();

    private LocalDateTime updatedAt;
    private String updatedBy;
}

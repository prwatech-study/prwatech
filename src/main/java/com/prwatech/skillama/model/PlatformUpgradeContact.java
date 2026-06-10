package com.prwatech.skillama.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/** Singleton admin-managed upgrade / full-access contact shown to freemium learners. */
@Data
@Document(collection = "platform_upgrade_contact")
public class PlatformUpgradeContact {
    public static final String SINGLETON_ID = "PLATFORM_UPGRADE_CONTACT";

    @Id
    private String id = SINGLETON_ID;
    private String contactEmail;
    private String contactMessage;
    private String mailtoSubject;
    private LocalDateTime updatedAt;
    private String updatedBy;
}

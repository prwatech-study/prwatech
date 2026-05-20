package com.prwatech.skillama.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Singleton admin-managed copy for referral sharing (WhatsApp, email, social).
 */
@Data
@Document(collection = "platform_referral_share")
public class PlatformReferralShare {
    public static final String SINGLETON_ID = "PLATFORM_REFERRAL_SHARE";

    @Id
    private String id = SINGLETON_ID;
    private String title;
    /** Message template; {@code {code}} and {@code {link}} are replaced when sharing. */
    private String shareMessage;
    private LocalDateTime updatedAt;
    private String updatedBy;
}

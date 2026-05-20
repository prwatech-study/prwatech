package com.prwatech.skillama.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "referral_share_events")
public class ReferralShareEvent {
    @Id
    private String id;
    private String userId;
    private String referralCode;
    /** WHATSAPP, EMAIL, COPY_LINK, TWITTER, LINKEDIN, OTHER */
    private String channel;
    private LocalDateTime createdAt = LocalDateTime.now();
}

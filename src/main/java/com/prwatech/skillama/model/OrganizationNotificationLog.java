package com.prwatech.skillama.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "organization_notification_logs")
public class OrganizationNotificationLog {
    @Id
    private String id;

    @Indexed
    private String organizationId;

    private String activityLogId;
    private String eventType;

    public enum RecipientType {
        ORG_CONTACT, ORG_OWNER, SALES_TEAM
    }

    private RecipientType recipientType;
    private String recipientEmail;
    private String subject;

    public enum DeliveryStatus {
        SENT, FAILED, SKIPPED
    }

    @Indexed
    private DeliveryStatus status;

    private String failureReason;

    @Indexed
    private LocalDateTime sentAt;
}

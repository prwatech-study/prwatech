package com.prwatech.skillama.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "admin_audit_logs")
public class AdminAuditLog {
    @Id
    private String id;

    @Indexed
    private String actorId;
    private String actorEmail;
    private String actorRole;

    @Indexed
    private String action;

    private String entityType;
    private String entityId;
    private String summary;
    private String detailsJson;

    @Indexed
    private LocalDateTime createdAt;
}

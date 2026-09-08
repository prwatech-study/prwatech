package com.prwatech.skillama.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "organization_activity_logs")
@CompoundIndex(name = "org_activity_created", def = "{'organizationId': 1, 'createdAt': -1}")
public class OrganizationActivityLog {
    @Id
    private String id;

    @Indexed
    private String organizationId;

    @Indexed
    private String eventType;

    private String summary;
    private String detailsJson;
    private String actorId;
    private String actorEmail;
    private String actorRole;
    private String entityType;
    private String entityId;
    @Builder.Default
    private boolean emailSent = false;

    @Indexed
    private LocalDateTime createdAt;
}

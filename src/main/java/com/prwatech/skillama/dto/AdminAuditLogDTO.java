package com.prwatech.skillama.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AdminAuditLogDTO {
    private String id;
    private String actorId;
    private String actorEmail;
    private String actorRole;
    private String action;
    private String entityType;
    private String entityId;
    private String summary;
    private String detailsJson;
    private LocalDateTime createdAt;
}

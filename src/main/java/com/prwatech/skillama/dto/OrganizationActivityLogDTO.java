package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationActivityLogDTO {
    private String id;
    private String eventType;
    private String summary;
    private String actorEmail;
    private String actorRole;
    private LocalDateTime createdAt;
}

package com.prwatech.skillama.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Archive snapshot when a user is permanently deleted (OWNER-initiated hard delete).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "deleted_skillama_users")
public class DeletedSkillamaUser {
    @Id
    private String id;
    private String originalUserId;
    private String email;
    private String name;
    private User.UserRole role;
    private User.PlanTier planTier;
    private String deletedByAdminId;
    private String deletedByAdminEmail;
    private String reason;
    private LocalDateTime deletedAt;
}

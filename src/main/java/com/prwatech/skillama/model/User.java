package com.prwatech.skillama.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")
public class User {
    @Id
    private String id;
    private String name;
    private String email;
    private String password;
    private boolean active;
    private String activationKey;
    private GenderEnum gender;

    @Indexed
    private UserRole role; // USER, ADMIN, OWNER (defaults to USER)

    private PlanTier planTier;
    private String phone;
    private Boolean emailVerified;
    @Indexed(unique = true, sparse = true)
    private String referralCode;
    private String referredBy;
    @Builder.Default
    private Integer queryCreditsUsed = 0;
    private Integer queryCreditsLimit;
    @Builder.Default
    private List<String> enabledModules = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastLoginAt;
    private String createdBy;
    private String updatedBy;

    public enum UserRole {
        USER, ADMIN, OWNER
    }

    public enum PlanTier {
        FREEMIUM, PAID, ENTERPRISE
    }
}

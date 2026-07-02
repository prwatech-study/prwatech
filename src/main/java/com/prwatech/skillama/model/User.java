package com.prwatech.skillama.model;

import lombok.*;
import com.fasterxml.jackson.annotation.JsonProperty;
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
    /** Accept on login/register requests; never include in API responses. */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
    private boolean active;
    private String activationKey;
    private GenderEnum gender;

    @Indexed
    private UserRole role; // USER, ADMIN, OWNER (defaults to USER)

    private PlanTier planTier;
    private String phone;
    /** Freemium course picked at signup; immutable once set. */
    private String chosenFreemiumCourseId;
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
    @Builder.Default
    private Integer loginCount = 0;
    private String createdBy;
    private String updatedBy;

    /** Learner LMS UI theme: classic | aurora */
    private String lmsThemePreference;

    /** OAuth provider subject IDs (sparse unique indexes). */
    @Indexed(unique = true, sparse = true)
    private String googleSub;
    @Indexed(unique = true, sparse = true)
    private String appleSub;
    private AuthProvider authProvider;
    private String profileImageUrl;
    @Builder.Default
    private Boolean onboardingCompleted = false;
    private LocalDateTime onboardingCompletedAt;

    /**
     * Per-module CRUD grants for ADMIN users. Null or empty = legacy full access (same as today).
     * OWNER ignores this field (always full access).
     */
    @Builder.Default
    private List<AdminModulePermission> adminModulePermissions = new ArrayList<>();

    public enum AuthProvider {
        EMAIL, GOOGLE, APPLE
    }

    public enum UserRole {
        USER, ADMIN, OWNER
    }

    /** Null/missing role in Mongo is treated as learner (USER). */
    public UserRole getEffectiveRole() {
        return role != null ? role : UserRole.USER;
    }

    public enum PlanTier {
        FREEMIUM, PAID, ENTERPRISE
    }
}

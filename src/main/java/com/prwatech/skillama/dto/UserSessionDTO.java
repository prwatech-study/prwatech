package com.prwatech.skillama.dto;

import com.prwatech.skillama.model.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Lightweight session snapshot for auth refresh — no token, no admin stats.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSessionDTO {
    private String id;
    private String name;
    private String email;
    private User.UserRole role;
    private Boolean active;
    private User.PlanTier planTier;
    private String gender;
    private Boolean onboardingRequired;
    private String phone;
    private String chosenFreemiumCourseId;
    private String profileImageUrl;
    private Boolean aiTutorIntroSeen;
    private Boolean demoVideoSeen;
}

package com.prwatech.skillama.dto;

import com.prwatech.skillama.model.GenderEnum;
import com.prwatech.skillama.model.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponseDTO {
    private String id;
    private String name;
    private String email;
    private User.UserRole role;
    private Boolean active;
    private GenderEnum gender;
    private LocalDateTime createdAt;
    private User.PlanTier planTier;
    private String token; // JWT access token
    private Boolean onboardingRequired;
    private String phone;
    private String chosenFreemiumCourseId;
}


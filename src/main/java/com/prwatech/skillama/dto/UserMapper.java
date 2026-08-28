package com.prwatech.skillama.dto;

import com.prwatech.skillama.model.User;
import com.prwatech.skillama.service.OnboardingService;

public final class UserMapper {

    private UserMapper() {}

    public static UserSessionDTO toSessionDto(User user) {
        return toSessionDto(user, new OnboardingService());
    }

    public static UserSessionDTO toSessionDto(User user, OnboardingService onboardingService) {
        if (user == null) {
            return null;
        }
        OnboardingService onboarding = onboardingService != null ? onboardingService : new OnboardingService();
        return UserSessionDTO.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole() != null ? user.getRole() : User.UserRole.USER)
                .active(user.isActive())
                .planTier(user.getPlanTier())
                .gender(user.getGender() != null ? user.getGender().name() : null)
                .onboardingRequired(onboarding.isOnboardingRequired(user))
                .phone(user.getPhone())
                .chosenFreemiumCourseId(user.getChosenFreemiumCourseId())
                .profileImageUrl(user.getProfileImageUrl())
                .aiTutorIntroSeen(user.getAiTutorIntroSeenAt() != null)
                .demoVideoSeen(user.getDemoVideoSeenAt() != null)
                .build();
    }

    public static LoginResponseDTO toLoginResponse(User user, String token, OnboardingService onboardingService) {
        if (user == null) {
            return null;
        }
        OnboardingService onboarding = onboardingService != null ? onboardingService : new OnboardingService();
        return LoginResponseDTO.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole() != null ? user.getRole() : User.UserRole.USER)
                .active(user.isActive())
                .gender(user.getGender())
                .createdAt(user.getCreatedAt())
                .planTier(user.getPlanTier())
                .token(token)
                .onboardingRequired(onboarding.isOnboardingRequired(user))
                .phone(user.getPhone())
                .chosenFreemiumCourseId(user.getChosenFreemiumCourseId())
                .aiTutorIntroSeen(user.getAiTutorIntroSeenAt() != null)
                .demoVideoSeen(user.getDemoVideoSeenAt() != null)
                .build();
    }

    public static UserPublicDTO toPublicDto(User user) {
        if (user == null) {
            return null;
        }
        return UserPublicDTO.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole() != null ? user.getRole() : User.UserRole.USER)
                .active(user.isActive())
                .planTier(user.getPlanTier())
                .build();
    }
}

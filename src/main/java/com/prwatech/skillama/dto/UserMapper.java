package com.prwatech.skillama.dto;

import com.prwatech.skillama.model.User;

public final class UserMapper {

    private UserMapper() {}

    public static UserSessionDTO toSessionDto(User user) {
        if (user == null) {
            return null;
        }
        return UserSessionDTO.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole() != null ? user.getRole() : User.UserRole.USER)
                .active(user.isActive())
                .planTier(user.getPlanTier())
                .gender(user.getGender() != null ? user.getGender().name() : null)
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

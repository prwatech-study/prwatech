package com.prwatech.skillama.dto;

import com.prwatech.skillama.model.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Safe user projection for authorized reads — never includes password or internal keys.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPublicDTO {
    private String id;
    private String name;
    private String email;
    private User.UserRole role;
    private Boolean active;
    private User.PlanTier planTier;
}

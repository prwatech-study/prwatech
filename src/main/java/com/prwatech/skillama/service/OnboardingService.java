package com.prwatech.skillama.service;

import com.prwatech.skillama.model.User;
import org.springframework.stereotype.Component;

/**
 * Determines whether a user must complete the post-login onboarding wizard.
 */
@Component
public class OnboardingService {

    public boolean isOnboardingRequired(User user) {
        if (user == null) {
            return true;
        }
        User.UserRole role = user.getEffectiveRole();
        if (role == User.UserRole.ADMIN || role == User.UserRole.OWNER) {
            return false;
        }
        if (Boolean.TRUE.equals(user.getOnboardingCompleted())) {
            return false;
        }
        return !hasCompleteLearnerProfile(user);
    }

    public boolean hasCompleteLearnerProfile(User user) {
        if (user.getName() == null || user.getName().isBlank()) {
            return false;
        }
        if (user.getPhone() == null || user.getPhone().isBlank()) {
            return false;
        }
        if (user.getPlanTier() == User.PlanTier.FREEMIUM) {
            return user.getChosenFreemiumCourseId() != null
                    && !user.getChosenFreemiumCourseId().isBlank();
        }
        return true;
    }

    public void markOnboardingComplete(User user) {
        user.setOnboardingCompleted(true);
    }
}

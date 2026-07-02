package com.prwatech.skillama.service;

import com.prwatech.skillama.model.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OnboardingServiceTest {

    private final OnboardingService service = new OnboardingService();

    @Test
    void adminNeverRequiresOnboarding() {
        User admin = User.builder()
                .role(User.UserRole.ADMIN)
                .onboardingCompleted(false)
                .build();
        assertFalse(service.isOnboardingRequired(admin));
    }

    @Test
    void freemiumIncompleteProfileRequiresOnboarding() {
        User user = User.builder()
                .role(User.UserRole.USER)
                .planTier(User.PlanTier.FREEMIUM)
                .name("Ada")
                .build();
        assertTrue(service.isOnboardingRequired(user));
    }

    @Test
    void freemiumCompleteProfileDoesNotRequireOnboarding() {
        User user = User.builder()
                .role(User.UserRole.USER)
                .planTier(User.PlanTier.FREEMIUM)
                .name("Ada")
                .phone("+911234567890")
                .chosenFreemiumCourseId("course-1")
                .onboardingCompleted(true)
                .build();
        assertFalse(service.isOnboardingRequired(user));
    }
}

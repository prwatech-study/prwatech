package com.prwatech.skillama.script;

import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.service.OnboardingService;
import com.prwatech.skillama.util.IndiaTime;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Backfills onboardingCompleted for existing users who already have a complete profile.
 */
@Component
@AllArgsConstructor
public class OnboardingBackfillScript implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(OnboardingBackfillScript.class);
    private static final boolean AUTO_RUN_BACKFILL = true;

    private final SkillamaUserRepository userRepository;
    private final OnboardingService onboardingService;

    @Override
    public void run(String... args) {
        if (!AUTO_RUN_BACKFILL) {
            return;
        }
        backfillOnboardingCompleted();
    }

    public void backfillOnboardingCompleted() {
        List<User> users = userRepository.findAll();
        int updated = 0;
        for (User user : users) {
            if (Boolean.TRUE.equals(user.getOnboardingCompleted())) {
                continue;
            }
            User.UserRole role = user.getEffectiveRole();
            if (role == User.UserRole.ADMIN || role == User.UserRole.OWNER) {
                user.setOnboardingCompleted(true);
                user.setOnboardingCompletedAt(IndiaTime.now());
                userRepository.save(user);
                updated++;
                continue;
            }
            if (onboardingService.hasCompleteLearnerProfile(user)) {
                user.setOnboardingCompleted(true);
                user.setOnboardingCompletedAt(IndiaTime.now());
                userRepository.save(user);
                updated++;
            }
        }
        if (updated > 0) {
            LOGGER.info("Onboarding backfill: marked {} users as onboarding complete", updated);
        }
    }
}

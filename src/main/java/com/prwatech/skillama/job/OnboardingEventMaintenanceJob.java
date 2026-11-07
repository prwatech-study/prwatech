package com.prwatech.skillama.job;

import com.prwatech.skillama.service.OnboardingEventService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OnboardingEventMaintenanceJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(OnboardingEventMaintenanceJob.class);

    private final OnboardingEventService onboardingEventService;

    @Scheduled(cron = "0 15 6 * * *", zone = "Asia/Kolkata")
    public void expireAndRemind() {
        try {
            onboardingEventService.expireDueMemberships();
            onboardingEventService.sendDueWeeklyAlarms();
        } catch (RuntimeException ex) {
            LOGGER.warn("Event maintenance failed: {}", ex.getMessage());
        }
    }
}

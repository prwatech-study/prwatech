package com.prwatech.skillama.job;

import com.prwatech.skillama.service.LearnerEngagementService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily streak reminder / loss emails (India time).
 * Reminder: active yesterday, not today. Loss: missed a calendar day.
 */
@Component
@RequiredArgsConstructor
public class LearningStreakEmailJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(LearningStreakEmailJob.class);

    private final LearnerEngagementService learnerEngagementService;

    @Scheduled(cron = "0 0 18 * * *", zone = "Asia/Kolkata")
    public void sendStreakEmails() {
        try {
            learnerEngagementService.processStreakEmails();
        } catch (Exception e) {
            LOGGER.warn("Learning streak email job failed: {}", e.getMessage());
        }
    }
}

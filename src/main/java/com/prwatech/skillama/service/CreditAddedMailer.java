package com.prwatech.skillama.service;

import com.prwatech.skillama.model.User;
import com.prwatech.skillama.notification.NotificationEventType;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Emails the learner when credits are actually added. A failed send never
 * rolls back the credit grant.
 */
@Service
@RequiredArgsConstructor
public class CreditAddedMailer {

    private static final Logger LOGGER = LoggerFactory.getLogger(CreditAddedMailer.class);
    /** Same display rate as the LMS wallet: 1 USD of wallet = 100 credits. */
    private static final double USD_TO_CREDITS_RATE = 100.0;

    public enum Source {
        ADMIN,
        RECHARGE,
        REFERRAL,
        SHARE
    }

    private final NotificationSettingsService notificationSettingsService;

    public void notifyAdded(User user, double addedUsd, Source source) {
        try {
            if (user == null || addedUsd <= 0 || !StringUtils.hasText(user.getEmail())) {
                return;
            }
            if (!notificationSettingsService.isEnabled(NotificationEventType.CREDITS_ADDED)) {
                LOGGER.debug("Skipping credits-added email; notification is off");
                return;
            }
            String credits = LowCreditNotice.formatCredits(usdToCredits(addedUsd));
            String name = StringUtils.hasText(user.getName()) ? " " + user.getName().trim() : "";
            String message = "Hi" + name + ",\n\n"
                    + lineFor(source, credits) + "\n\n"
                    + "You can continue learning on Skillama.\n\n"
                    + "— Skillama";
            notificationSettingsService.sendLearnerNotification(
                    NotificationEventType.CREDITS_ADDED,
                    user.getEmail().trim(),
                    "Credits added to your Skillama account",
                    message);
        } catch (Exception e) {
            LOGGER.error("Failed to email credits-added notice to {}", user == null ? null : user.getEmail(), e);
        }
    }

    private static String lineFor(Source source, String credits) {
        if (source == Source.RECHARGE) {
            return "Your recharge added " + credits + " credits to your account.";
        }
        if (source == Source.REFERRAL) {
            return "A referral added " + credits + " credits to your account.";
        }
        if (source == Source.SHARE) {
            return "Sharing a course added " + credits + " credits to your account.";
        }
        return "We added " + credits + " credits to your account.";
    }

    static double usdToCredits(double usd) {
        return Math.round(usd * USD_TO_CREDITS_RATE * 10.0) / 10.0;
    }
}

package com.prwatech.skillama.service;

import com.prwatech.skillama.model.User;
import com.prwatech.skillama.notification.NotificationEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditAddedMailerTest {

    @Mock private NotificationSettingsService notificationSettingsService;

    private CreditAddedMailer mailer;

    @BeforeEach
    void setUp() {
        mailer = new CreditAddedMailer(notificationSettingsService);
    }

    @Test
    void notifyAdded_adminTopUp_emailsCreditsNotDollars() {
        when(notificationSettingsService.isEnabled(NotificationEventType.CREDITS_ADDED)).thenReturn(true);
        User user = User.builder().email("supriya@x.com").name("Supriya").build();

        mailer.notifyAdded(user, 0.30, CreditAddedMailer.Source.ADMIN);

        verify(notificationSettingsService).sendLearnerNotification(
                eq(NotificationEventType.CREDITS_ADDED),
                eq("supriya@x.com"),
                eq("Credits added to your Skillama account"),
                contains("We added 30 credits to your account."));
    }

    @Test
    void notifyAdded_zeroOrMissingEmail_doesNotSend() {
        User user = User.builder().email(" ").build();
        mailer.notifyAdded(user, 0.30, CreditAddedMailer.Source.ADMIN);
        mailer.notifyAdded(User.builder().email("a@b.com").build(), 0, CreditAddedMailer.Source.RECHARGE);
        verify(notificationSettingsService, never()).sendLearnerNotification(
                eq(NotificationEventType.CREDITS_ADDED),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void usdToCredits_usesTheLearnerDisplayRate() {
        assertEquals(30.0, CreditAddedMailer.usdToCredits(0.30));
    }
}

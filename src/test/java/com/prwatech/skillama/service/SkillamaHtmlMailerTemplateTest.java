package com.prwatech.skillama.service;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillamaHtmlMailerTest {

    @Test
    void verificationMailLeadsWithTheCodeSoInboxesCanOfferCopy() {
        String plain = SkillamaHtmlMailer.verificationPlain("923037", 10);
        String html = SkillamaHtmlMailer.verificationHtml("923037", 10);

        assertTrue(plain.startsWith("923037"));
        assertTrue(plain.contains("verification code is 923037"));
        assertTrue(html.contains("923037"));
        assertTrue(html.contains("Skillama verification code"));
        assertTrue(html.contains("10 minutes"));
        assertTrue(html.contains("#f4f6f8"));
        assertTrue(html.contains("#0b1018"));
        assertTrue(html.contains("#34d399"));
        assertFalse(html.contains("#06080f"));
        assertFalse(html.contains("<script"));
    }

    @Test
    void lowCreditMailShowsTheBalanceAndARechargeLink() {
        String html = SkillamaHtmlMailer.lowCreditHtml("Jitendra", "7", "https://skillama.co.in/pricing");
        String plain = SkillamaHtmlMailer.lowCreditPlain("Jitendra", "7", "https://skillama.co.in/pricing");

        assertTrue(html.contains("LOW CREDITS"));
        assertTrue(html.contains(">7<"));
        assertTrue(html.contains("https://skillama.co.in/pricing"));
        assertTrue(html.contains("Recharge credits"));
        assertTrue(html.contains("Hi Jitendra"));
        assertTrue(plain.contains("Only 7 credits left"));
        assertTrue(plain.contains("https://skillama.co.in/pricing"));
    }

    @Test
    void sendsPlainAndHtmlAsOneMultipartMessage() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage((jakarta.mail.Session) null);
        when(mailSender.createMimeMessage()).thenReturn(message);
        SkillamaHtmlMailer mailer = new SkillamaHtmlMailer(mailSender);
        ReflectionTestUtils.setField(mailer, "fromAddress", "hello@skillama.co.in");
        ReflectionTestUtils.setField(mailer, "publicUrl", "https://skillama.co.in");

        SkillamaHtmlMailer.MailSendResult result = mailer.sendLowCredits("learner@example.com", "Jitendra", "7");

        assertTrue(result.isDelivered(), result.getError());
        verify(mailSender).send(message);
        assertTrue(message.getContent() instanceof jakarta.mail.internet.MimeMultipart);
    }

    @Test
    void streakMailComesFromTheSkillamaMailbox() throws Exception {
        String html = SkillamaHtmlMailer.learnerMessageHtml(
                "Don't lose your 3-day Skillama streak",
                "Hi Asha,\n\nYou have a 3-day learning streak.\n\n— Skillama");
        assertTrue(html.contains("Don't lose your 3-day Skillama streak"));
        assertTrue(html.contains("Hi Asha,"));
        assertTrue(html.contains("hello@skillama.co.in"));
        assertFalse(html.toLowerCase().contains("prwatech"));
        assertFalse(html.contains("Team Prwatech"));

        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage((jakarta.mail.Session) null);
        when(mailSender.createMimeMessage()).thenReturn(message);
        SkillamaHtmlMailer mailer = new SkillamaHtmlMailer(mailSender);
        ReflectionTestUtils.setField(mailer, "fromAddress", "hello@skillama.co.in");

        SkillamaHtmlMailer.MailSendResult result = mailer.sendMessage(
                "asha@x.com",
                "Don't lose your 3-day Skillama streak",
                "Hi Asha,\n\nYou have a 3-day learning streak.\n\n— Skillama");

        assertTrue(result.isDelivered(), result.getError());
        verify(mailSender).send(message);
        InternetAddress from = (InternetAddress) message.getFrom()[0];
        assertEquals("hello@skillama.co.in", from.getAddress());
        assertEquals("Skillama", from.getPersonal());
        assertEquals("asha@x.com", ((InternetAddress) message.getAllRecipients()[0]).getAddress());
    }

    @Test
    void messageHtmlTurnsParagraphsBulletsAndALinkIntoReadableBlocks() {
        String html = SkillamaHtmlMailer.formatPlainAsHtml(
                "Hi Asha,\n\n"
                        + "These courses are ready:\n\n"
                        + "• Python\n"
                        + "• SQL\n\n"
                        + "https://skillama.co.in/lms\n\n"
                        + "— Skillama");
        assertTrue(html.contains("<p"));
        assertTrue(html.contains("<ul"));
        assertTrue(html.contains("<li"));
        assertTrue(html.contains("Open Skillama"));
        assertTrue(html.contains("href=\"https://skillama.co.in/lms\""));
        assertFalse(html.contains("• Python"));
        assertFalse(html.toLowerCase().contains("prwatech"));
    }

    @Test
    void escapesNamesInHtml() {
        String html = SkillamaHtmlMailer.lowCreditHtml("<b>", "7", "https://skillama.co.in/pricing");
        assertFalse(html.contains("<b>"));
        assertTrue(html.contains("&lt;b&gt;"));
    }
}

package com.prwatech.skillama.service;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

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
    void escapesNamesInHtml() {
        String html = SkillamaHtmlMailer.lowCreditHtml("<b>", "7", "https://skillama.co.in/pricing");
        assertFalse(html.contains("<b>"));
        assertTrue(html.contains("&lt;b&gt;"));
    }
}

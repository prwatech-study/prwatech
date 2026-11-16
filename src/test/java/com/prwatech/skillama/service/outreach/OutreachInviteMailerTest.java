package com.prwatech.skillama.service.outreach;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OutreachInviteMailerTest {

    @Test
    void plainBodyContainsUnsubscribeAndCredits() {
        String plain = OutreachInviteMailer.buildPlainBody(
                1,
                "Alex",
                "https://skillama.co.in/welcome/prwatech?ocid=x",
                "https://api.example/skillama/public/outreach/unsubscribe/x",
                "https://skillama.co.in");
        assertTrue(plain.contains("100 AI credits"));
        assertTrue(plain.contains("Unsubscribe"));
        assertTrue(plain.contains("Alex"));
    }

    @Test
    void htmlBodyContainsCtaAndPixelPlaceholder() {
        String html = OutreachInviteMailer.buildHtmlBody(
                1,
                "Alex",
                "https://api.example/skillama/public/outreach/c/x",
                "https://api.example/skillama/public/outreach/o/x.gif",
                "https://api.example/skillama/public/outreach/unsubscribe/x",
                "https://skillama.co.in");
        assertTrue(html.contains("Claim 100 credits"));
        assertTrue(html.contains("outreach/o/x.gif"));
        assertTrue(html.contains("Unsubscribe"));
    }
}
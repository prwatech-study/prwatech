package com.prwatech.skillama.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void escapesNamesInHtml() {
        String html = SkillamaHtmlMailer.lowCreditHtml("<b>", "7", "https://skillama.co.in/pricing");
        assertFalse(html.contains("<b>"));
        assertTrue(html.contains("&lt;b&gt;"));
    }
}

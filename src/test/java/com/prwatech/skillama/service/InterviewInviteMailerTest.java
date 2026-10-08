package com.prwatech.skillama.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InterviewInviteMailerTest {

    @Test
    void inviteLinkAndIcsCarryTheDeepLinkAndSlot() {
        Instant start = Instant.parse("2026-10-08T10:00:00Z");
        Instant end = Instant.parse("2026-10-08T10:20:00Z");
        String link = InterviewInviteMailer.inviteLink("https://skillama.co.in/", "opaque-token");
        String ics = InterviewInviteMailer.buildIcs("opaque-token", start, end, link, "a@b.com", "Acme");

        assertTrue(link.equals("https://skillama.co.in/interview/opaque-token"));
        assertTrue(ics.contains("METHOD:REQUEST"));
        assertTrue(ics.contains("DTSTART:20261008T100000Z"));
        assertTrue(ics.contains("DTEND:20261008T102000Z"));
        assertTrue(ics.contains("URL:" + link));
    }
}

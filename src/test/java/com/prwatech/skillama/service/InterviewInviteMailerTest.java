package com.prwatech.skillama.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void plainBodyUsesIstAndCalendarDownloadLink() {
        Instant start = Instant.parse("2026-10-08T16:55:00Z");
        Instant end = Instant.parse("2026-10-08T17:15:00Z");
        String link = "https://skillama.co.in/interview/tok";
        String calendar = InterviewInviteMailer.calendarLink("https://skillama.co.in", "tok");
        String body = InterviewInviteMailer.plainBody(link, calendar, start, end, false);

        assertTrue(body.contains("IST"));
        assertFalse(body.contains("UTC"));
        // 16:55 UTC → 10:25 PM IST
        assertTrue(body.contains("10:25 PM IST"));
        assertTrue(body.contains("10:45 PM IST"));
        assertTrue(body.contains(calendar));
        assertFalse(body.contains("BEGIN:VCALENDAR"));
        assertFalse(body.contains("could not be attached"));
    }
}

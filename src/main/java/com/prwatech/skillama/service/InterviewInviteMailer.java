package com.prwatech.skillama.service;

import com.prwatech.common.dto.EmailSendDto;
import com.prwatech.common.service.impl.EmailServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** Email plus an ICS calendar invite. SMTP is preferred; the HTTP mail API is the fallback. */
@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewInviteMailer {

    private static final DateTimeFormatter ICS_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private final JavaMailSender mailSender;
    private final EmailServiceImpl emailService;

    @Value("${prwatech.default.email.id:}")
    private String fromAddress;

    @Value("${skillama.app.public-url:https://skillama.co.in}")
    private String publicUrl;

    public boolean sendInvite(String email, String token, Instant start, Instant end, String orgName) {
        String link = inviteLink(publicUrl, token);
        String ics = buildIcs(token, start, end, link, email, orgName);
        String subject = "Your Skillama AI Interview";
        String body = "Your AI Interview is scheduled.\n\nJoin with this link:\n" + link
                + "\n\nUse the same email address this invitation was sent to."
                + "\nCamera and microphone are required. The session ends at the scheduled stop time."
                + "\n\nIf your calendar did not add the event automatically, import the attached invite.";
        if (trySmtp(email, subject, body, ics)) {
            return true;
        }
        try {
            emailService.sendEmail(new EmailSendDto(email, subject, body + "\n\n" + ics));
        } catch (RuntimeException ex) {
            log.warn("Interview invite email failed for {}: {}", email, ex.getMessage());
            return false;
        }
        return false;
    }

    static String inviteLink(String publicUrl, String token) {
        String base = publicUrl == null ? "https://skillama.co.in" : publicUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/interview/" + token;
    }

    static String buildIcs(String uid, Instant start, Instant end, String link, String email, String orgName) {
        String summary = (orgName == null || orgName.isBlank())
                ? "Skillama AI Interview"
                : "Skillama AI Interview — " + orgName;
        String description = "Join: " + link;
        return "BEGIN:VCALENDAR\r\n"
                + "VERSION:2.0\r\n"
                + "PRODID:-//Skillama//AI Interview//EN\r\n"
                + "METHOD:REQUEST\r\n"
                + "BEGIN:VEVENT\r\n"
                + "UID:" + uid + "@skillama.co.in\r\n"
                + "DTSTAMP:" + ICS_TIME.format(Instant.now()) + "\r\n"
                + "DTSTART:" + ICS_TIME.format(start) + "\r\n"
                + "DTEND:" + ICS_TIME.format(end) + "\r\n"
                + "SUMMARY:" + escapeIcs(summary) + "\r\n"
                + "DESCRIPTION:" + escapeIcs(description) + "\r\n"
                + "URL:" + link + "\r\n"
                + "ATTENDEE;CN=" + escapeIcs(email) + ":MAILTO:" + email + "\r\n"
                + "END:VEVENT\r\n"
                + "END:VCALENDAR\r\n";
    }

    private boolean trySmtp(String email, String subject, String body, String ics) {
        if (fromAddress == null || fromAddress.isBlank()) {
            return false;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(email);
            helper.setSubject(subject);
            helper.setText(body, false);
            helper.addAttachment(
                    "interview.ics",
                    new ByteArrayResource(ics.getBytes(StandardCharsets.UTF_8)),
                    "text/calendar; method=REQUEST; charset=UTF-8");
            mailSender.send(message);
            return true;
        } catch (Exception ex) {
            log.warn("SMTP interview invite failed for {}: {}", email, ex.getMessage());
            return false;
        }
    }

    private static String escapeIcs(String value) {
        return value.replace("\\", "\\\\").replace("\n", "\\n").replace(",", "\\,").replace(";", "\\;");
    }
}

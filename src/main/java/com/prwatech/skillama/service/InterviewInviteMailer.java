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

import jakarta.activation.DataHandler;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Email plus an ICS calendar invite.
 * SMTP sends a real {@code .ics} attachment (and a {@code text/calendar} part so clients can
 * auto-add the event). The HTTP mail API cannot attach files, so that fallback must never paste
 * raw VCALENDAR into the body.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewInviteMailer {

    private static final DateTimeFormatter ICS_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter HUMAN_UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

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
        String bodyForSmtp = plainBody(link, start, end, true);
        if (trySmtp(email, subject, bodyForSmtp, ics)) {
            return true;
        }
        // HTTP support API only accepts {email, subject, message} — no attachments.
        try {
            emailService.sendEmail(new EmailSendDto(email, subject, plainBody(link, start, end, false)));
            return true;
        } catch (RuntimeException ex) {
            log.warn("Interview invite email failed for {}: {}", email, ex.getMessage());
            return false;
        }
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

    static String plainBody(String link, Instant start, Instant end, boolean hasCalendarAttachment) {
        StringBuilder body = new StringBuilder();
        body.append("Your AI Interview is scheduled.\n\n");
        body.append("When: ").append(HUMAN_UTC.format(start))
                .append(" – ").append(HUMAN_UTC.format(end)).append("\n\n");
        body.append("Join with this link:\n").append(link).append("\n\n");
        body.append("Use the same email address this invitation was sent to.\n");
        body.append("Camera and microphone are required. The session ends at the scheduled stop time.");
        if (hasCalendarAttachment) {
            body.append("\n\nA calendar invite (interview.ics) is attached — open it to add this to your calendar.");
        } else {
            body.append("\n\nAdd the time above to your calendar manually (a file invite could not be attached).");
        }
        return body.toString();
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

            // Extra calendar MIME part helps Outlook/Gmail offer "Add to calendar".
            MimeMultipart mixed = (MimeMultipart) message.getContent();
            MimeBodyPart calendarPart = new MimeBodyPart();
            calendarPart.setDataHandler(new DataHandler(
                    new ByteArrayDataSource(ics, "text/calendar; method=REQUEST; charset=UTF-8")));
            calendarPart.setHeader("Content-Class", "urn:content-classes:calendarmessage");
            calendarPart.setFileName("invite.ics");
            mixed.addBodyPart(calendarPart);

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

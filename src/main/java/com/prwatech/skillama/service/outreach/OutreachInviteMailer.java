package com.prwatech.skillama.service.outreach;

import com.prwatech.skillama.service.SkillamaHtmlMailer;
import com.prwatech.skillama.service.SkillamaHtmlMailer.MailSendResult;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
@Slf4j
public class OutreachInviteMailer {

    private final JavaMailSender mailSender;

    public OutreachInviteMailer(@Qualifier("skillamaMailSender") JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    private static final String PAGE = "#f4f6f8";
    private static final String CARD = "#0b1018";
    private static final String ACCENT = "#34d399";
    private static final String CYAN = "#22d3ee";
    private static final String TEXT = "#e5e7eb";
    private static final String MUTED = "#94a3b8";
    private static final String BUTTON = "#10b981";
    private static final String BUTTON_TEXT = "#042f2e";

    @Value("${skillama.mail.from:hello@skillama.co.in}")
    private String fromAddress;

    public static String subjectForWave(int wave) {
        return switch (wave) {
            case 2 -> "Reminder: your 100 free AI credits on Skillama are still waiting";
            case 3 -> "Last reminder — Prwatech students get 100 AI credits on Skillama";
            default -> "You learned with Prwatech — try Skillama with 100 free AI credits";
        };
    }

    public static String buildPlainBody(int wave, String firstName, String inviteUrl, String unsubscribeUrl, String publicSite) {
        String who = firstName == null || firstName.isBlank() ? "there" : firstName.trim();
        String intro = wave == 1
                ? "You were part of the Prwatech learning community. We have built Skillama — a hands-on AI learning platform where you can study with an AI tutor, run code, take quizzes, and earn certifications."
                : "We invited you earlier to try Skillama — hands-on AI learning with your own tutor, code practice, and quizzes.";
        return "Hi " + who + ",\n\n"
                + intro + "\n\n"
                + "As a former Prwatech student, you can try Skillama free and get 100 AI credits when you create your account using this invite link (credits apply only through this link):\n\n"
                + inviteUrl + "\n\n"
                + "What you get on Skillama:\n"
                + "• Learn with an AI tutor on real course content\n"
                + "• Practice code and quizzes inside the LMS\n"
                + "• Start on the free plan and explore at your own pace\n\n"
                + "If the link does not open, copy and paste it into your browser.\n\n"
                + "Questions? Reply to this email or write to hello@skillama.co.in.\n\n"
                + "— Team Skillama\n"
                + publicSite + "\n\n"
                + "Privacy: " + publicSite + "/privacy\n\n"
                + "Unsubscribe from Skillama invite emails:\n"
                + unsubscribeUrl + "\n";
    }

    public static String buildHtmlBody(int wave, String firstName, String inviteUrl, String openPixelUrl,
                                       String unsubscribeUrl, String publicSite) {
        String who = escape(firstName == null || firstName.isBlank() ? "there" : firstName.trim());
        String title = wave == 1
                ? "Your Prwatech learning continues on Skillama"
                : (wave == 2 ? "Your 100 credits are still waiting" : "Last chance for 100 free AI credits");
        String lead = wave == 1
                ? "Hi " + who + ", try our new hands-on AI platform built for learners like you."
                : "Hi " + who + ", we invited you earlier to try Skillama.";
        return "<!DOCTYPE html><html><body bgcolor=\"" + PAGE + "\" style=\"margin:0;background:" + PAGE + ";font-family:Arial,Helvetica,sans-serif;\">"
                + "<div style=\"display:none;max-height:0;overflow:hidden;color:" + PAGE + ";\">"
                + "Hands-on AI courses and your own tutor. Sign up from this email to unlock 100 credits."
                + "</div>"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" bgcolor=\"" + PAGE + "\" style=\"background:" + PAGE + ";padding:24px 12px;\">"
                + "<tr><td align=\"center\">"
                + "<table role=\"presentation\" width=\"100%\" bgcolor=\"" + CARD + "\" style=\"max-width:520px;background:" + CARD + ";border-radius:12px;overflow:hidden;\">"
                + "<tr><td style=\"height:4px;background:" + ACCENT + ";font-size:0;\">&nbsp;</td>"
                + "<td style=\"height:4px;background:" + CYAN + ";font-size:0;\">&nbsp;</td></tr>"
                + "<tr><td colspan=\"2\" style=\"padding:24px;color:" + TEXT + ";font-size:15px;line-height:1.5;\">"
                + "<div style=\"color:" + ACCENT + ";font-size:13px;font-weight:700;letter-spacing:0.08em;\">SKILLAMA</div>"
                + "<div style=\"color:#ffffff;font-size:22px;font-weight:700;padding:8px 0 12px;\">" + escape(title) + "</div>"
                + "<p style=\"margin:0 0 12px;\">" + lead + "</p>"
                + "<div style=\"background:#064e3b;color:#a7f3d0;font-size:12px;font-weight:700;padding:8px 12px;border-radius:8px;display:inline-block;margin:0 0 16px;\">"
                + "100 free AI credits when you sign up from this email</div>"
                + "<ul style=\"margin:0 0 16px;padding-left:18px;color:" + TEXT + ";\">"
                + "<li style=\"margin:0 0 6px;\">AI tutor on real course content</li>"
                + "<li style=\"margin:0 0 6px;\">Code practice and quizzes</li>"
                + "<li style=\"margin:0 0 6px;\">Start free, learn at your pace</li>"
                + "</ul>"
                + "<a href=\"" + escape(inviteUrl) + "\" style=\"display:inline-block;background:" + BUTTON + ";color:" + BUTTON_TEXT
                + ";text-decoration:none;font-weight:700;padding:12px 18px;border-radius:8px;\">Claim 100 credits &amp; start free</a>"
                + "<p style=\"margin:16px 0 0;font-size:12px;color:" + MUTED + ";\">"
                + "<a href=\"" + escape(publicSite) + "/privacy\" style=\"color:" + ACCENT + ";\">Privacy</a> · "
                + "<a href=\"" + escape(unsubscribeUrl) + "\" style=\"color:" + MUTED + ";\">Unsubscribe</a>"
                + "</p></td></tr>"
                + "<tr><td colspan=\"2\" style=\"padding:0 24px 20px;color:" + MUTED + ";font-size:12px;\">Skillama · hello@skillama.co.in</td></tr>"
                + "</table></td></tr></table>"
                + "<img src=\"" + escape(openPixelUrl) + "\" width=\"1\" height=\"1\" alt=\"\" style=\"display:block;border:0;\" />"
                + "</body></html>";
    }

    public MailSendResult send(String to, String subject, String plain, String html, String listUnsubscribeUrl) {
        if (fromAddress == null || fromAddress.isBlank()) {
            return MailSendResult.failed("Skillama from address is not configured");
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(fromAddress, "Skillama"));
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(plain, html);
            if (listUnsubscribeUrl != null && !listUnsubscribeUrl.isBlank()) {
                message.setHeader("List-Unsubscribe", "<" + listUnsubscribeUrl + ">");
                message.setHeader("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
            }
            mailSender.send(message);
            return MailSendResult.sent();
        } catch (Exception ex) {
            log.warn("Outreach SMTP send failed for {}: {}", to, ex.getMessage());
            return MailSendResult.failed(ex.getMessage());
        }
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
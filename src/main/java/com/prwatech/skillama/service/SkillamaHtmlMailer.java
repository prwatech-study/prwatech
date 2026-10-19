package com.prwatech.skillama.service;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * Skillama learner mail only, from hello@skillama.co.in.
 * Prwatech mail does not use this sender.
 */
@Service
@Slf4j
public class SkillamaHtmlMailer {

    private static final String HEADER = "#0f766e";
    private static final String BUTTON = "#0d9488";

    private final JavaMailSender mailSender;

    @Value("${skillama.mail.from:hello@skillama.co.in}")
    private String fromAddress;

    @Value("${skillama.app.public-url:https://skillama.co.in}")
    private String publicUrl;

    public SkillamaHtmlMailer(@Qualifier("skillamaMailSender") JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Getter
    public static final class MailSendResult {
        private final boolean delivered;
        private final String error;

        private MailSendResult(boolean delivered, String error) {
            this.delivered = delivered;
            this.error = error;
        }

        public static MailSendResult sent() {
            return new MailSendResult(true, null);
        }

        public static MailSendResult failed(String error) {
            return new MailSendResult(false, error == null || error.isBlank() ? "Send failed" : error);
        }
    }

    public void sendVerificationCode(String email, String code, int expiryMinutes) {
        String subject = "Skillama verification code";
        String plain = verificationPlain(code, expiryMinutes);
        String html = verificationHtml(code, expiryMinutes);
        send(email, subject, plain, html);
    }

    public MailSendResult sendLowCredits(String email, String name, String creditsLeft) {
        String subject = "Low AI credits remaining";
        String rechargeUrl = trimBase(publicUrl) + "/pricing";
        String plain = lowCreditPlain(name, creditsLeft, rechargeUrl);
        String html = lowCreditHtml(name, creditsLeft, rechargeUrl);
        return send(email, subject, plain, html);
    }

    static String verificationPlain(String code, int expiryMinutes) {
        return code + "\n\n"
                + "Your Skillama verification code is " + code + ".\n\n"
                + "This code expires in " + expiryMinutes + " minutes.\n"
                + "If you did not ask for this code, you can ignore this email.\n";
    }

    static String verificationHtml(String code, int expiryMinutes) {
        String safeCode = escape(code);
        return "<!DOCTYPE html><html><body style=\"margin:0;background:#f4f6f8;font-family:Arial,Helvetica,sans-serif;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f4f6f8;padding:24px 12px;\">"
                + "<tr><td align=\"center\">"
                + "<table role=\"presentation\" width=\"100%\" style=\"max-width:480px;background:#ffffff;border-radius:12px;overflow:hidden;\">"
                + "<tr><td style=\"background:" + HEADER + ";padding:28px 24px;\">"
                + "<div style=\"color:#ffffff;font-size:22px;font-weight:700;\">Skillama verification code</div>"
                + "</td></tr>"
                + "<tr><td style=\"padding:28px 24px;color:#1f2937;font-size:15px;line-height:1.5;\">"
                + "<p style=\"margin:0 0 16px;\">We received a request to sign in to your Skillama account.</p>"
                + "<p style=\"margin:0 0 8px;color:#6b7280;font-size:13px;\">Your code</p>"
                + "<div style=\"font-size:36px;font-weight:700;letter-spacing:8px;color:#111827;padding:8px 0 16px;\">"
                + safeCode + "</div>"
                + "<p style=\"margin:0;color:#6b7280;font-size:13px;\">This code expires in "
                + expiryMinutes + " minutes. If you did not ask for it, you can ignore this email.</p>"
                + "</td></tr></table></td></tr></table></body></html>";
    }

    static String lowCreditPlain(String name, String creditsLeft, String rechargeUrl) {
        return "Hi " + displayName(name) + ",\n\n"
                + "You have low AI credits remaining. Only " + creditsLeft + " credits left.\n\n"
                + "Recharge now so learning is not interrupted:\n"
                + rechargeUrl + "\n";
    }

    static String lowCreditHtml(String name, String creditsLeft, String rechargeUrl) {
        String safeName = escape(displayName(name));
        String safeCredits = escape(creditsLeft);
        String safeUrl = escape(rechargeUrl);
        return "<!DOCTYPE html><html><body style=\"margin:0;background:#f4f6f8;font-family:Arial,Helvetica,sans-serif;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f4f6f8;padding:24px 12px;\">"
                + "<tr><td align=\"center\">"
                + "<table role=\"presentation\" width=\"100%\" style=\"max-width:520px;background:#ffffff;border-radius:12px;overflow:hidden;\">"
                + "<tr><td style=\"background:" + HEADER + ";padding:22px 24px;\">"
                + "<div style=\"color:#ffffff;font-size:20px;font-weight:700;\">Skillama</div>"
                + "</td></tr>"
                + "<tr><td style=\"padding:24px;color:#1f2937;font-size:15px;line-height:1.5;\">"
                + "<p style=\"margin:0 0 16px;\">Hi " + safeName + ",</p>"
                + "<div style=\"background:#fef2f2;color:#b91c1c;font-size:12px;font-weight:700;letter-spacing:0.04em;"
                + "padding:8px 12px;border-radius:8px;display:inline-block;\">LOW CREDITS</div>"
                + "<p style=\"margin:16px 0 8px;\">You have low AI credits remaining.</p>"
                + "<p style=\"margin:0 0 16px;\">Only <strong>" + safeCredits + "</strong> credits left.</p>"
                + "<table role=\"presentation\" style=\"background:#f0fdfa;border-radius:10px;margin:0 0 16px;\"><tr>"
                + "<td style=\"padding:14px 18px;color:#115e59;font-size:13px;\">Credits remaining</td>"
                + "<td style=\"padding:14px 18px;font-size:28px;font-weight:700;color:#0f766e;\">" + safeCredits + "</td>"
                + "</tr></table>"
                + "<p style=\"margin:0 0 16px;\">Recharge now to avoid any interruption in your learning.</p>"
                + "<a href=\"" + safeUrl + "\" style=\"display:inline-block;background:" + BUTTON + ";color:#ffffff;"
                + "text-decoration:none;font-weight:700;padding:12px 18px;border-radius:8px;\">Recharge credits</a>"
                + "</td></tr>"
                + "<tr><td style=\"padding:0 24px 20px;color:#9ca3af;font-size:12px;\">Skillama · hello@skillama.co.in</td>"
                + "</tr></table></td></tr></table></body></html>";
    }

    private MailSendResult send(String email, String subject, String plain, String html) {
        if (fromAddress == null || fromAddress.isBlank()) {
            return MailSendResult.failed("Skillama from address is not configured");
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(fromAddress, "Skillama"));
            helper.setTo(email);
            helper.setSubject(subject);
            helper.setText(plain, html);
            mailSender.send(message);
            return MailSendResult.sent();
        } catch (Exception ex) {
            log.warn("Skillama SMTP send failed for {}: {}", email, ex.getMessage());
            return MailSendResult.failed(ex.getMessage());
        }
    }

    private static String displayName(String name) {
        if (name == null || name.isBlank()) {
            return "there";
        }
        return name.trim();
    }

    private static String trimBase(String publicUrl) {
        String base = publicUrl == null ? "https://skillama.co.in" : publicUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}

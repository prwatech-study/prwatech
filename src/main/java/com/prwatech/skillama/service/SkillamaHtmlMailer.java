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

private static final String CANVAS = "#06080f";
    private static final String CARD = "#0b1018";
    private static final String ACCENT = "#34d399";
    private static final String CYAN = "#22d3ee";
    private static final String TEXT = "#e5e7eb";
    private static final String MUTED = "#94a3b8";
    private static final String BUTTON = "#10b981";
    private static final String BUTTON_TEXT = "#042f2e";

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
        return "<!DOCTYPE html><html><body style=\"margin:0;background:" + CANVAS + ";font-family:Arial,Helvetica,sans-serif;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:" + CANVAS + ";padding:24px 12px;\">"
                + "<tr><td align=\"center\">"
                + "<table role=\"presentation\" width=\"100%\" style=\"max-width:480px;background:" + CARD + ";border-radius:12px;overflow:hidden;\">"
                + "<tr><td style=\"height:4px;background:" + ACCENT + ";font-size:0;line-height:0;\">&nbsp;</td>"
                + "<td style=\"height:4px;background:" + CYAN + ";font-size:0;line-height:0;\">&nbsp;</td></tr>"
                + "<tr><td colspan=\"2\" style=\"padding:28px 24px;color:" + TEXT + ";font-size:15px;line-height:1.5;\">"
                + "<div style=\"color:" + ACCENT + ";font-size:13px;font-weight:700;letter-spacing:0.08em;\">SKILLAMA</div>"
                + "<div style=\"color:#ffffff;font-size:22px;font-weight:700;padding:8px 0 16px;\">Skillama verification code</div>"
                + "<p style=\"margin:0 0 16px;\">We received a request to sign in to your Skillama account.</p>"
                + "<p style=\"margin:0 0 8px;color:" + MUTED + ";font-size:13px;\">Your code</p>"
                + "<div style=\"font-size:36px;font-weight:700;letter-spacing:8px;color:" + ACCENT + ";padding:8px 0 16px;\">"
                + safeCode + "</div>"
                + "<p style=\"margin:0;color:" + MUTED + ";font-size:13px;\">This code expires in "
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
        return "<!DOCTYPE html><html><body style=\"margin:0;background:" + CANVAS + ";font-family:Arial,Helvetica,sans-serif;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:" + CANVAS + ";padding:24px 12px;\">"
                + "<tr><td align=\"center\">"
                + "<table role=\"presentation\" width=\"100%\" style=\"max-width:520px;background:" + CARD + ";border-radius:12px;overflow:hidden;\">"
                + "<tr><td style=\"height:4px;background:" + ACCENT + ";font-size:0;line-height:0;\">&nbsp;</td>"
                + "<td style=\"height:4px;background:" + CYAN + ";font-size:0;line-height:0;\">&nbsp;</td></tr>"
                + "<tr><td colspan=\"2\" style=\"padding:24px;color:" + TEXT + ";font-size:15px;line-height:1.5;\">"
                + "<div style=\"color:" + ACCENT + ";font-size:13px;font-weight:700;letter-spacing:0.08em;\">SKILLAMA</div>"
                + "<p style=\"margin:16px 0;\">Hi " + safeName + ",</p>"
                + "<div style=\"background:#3f1d24;color:#fecaca;font-size:12px;font-weight:700;letter-spacing:0.04em;"
                + "padding:8px 12px;border-radius:8px;display:inline-block;\">LOW CREDITS</div>"
                + "<p style=\"margin:16px 0 8px;\">You have low AI credits remaining.</p>"
                + "<p style=\"margin:0 0 16px;\">Only <strong>" + safeCredits + "</strong> credits left.</p>"
                + "<table role=\"presentation\" style=\"background:#111827;border-radius:10px;margin:0 0 16px;\"><tr>"
                + "<td style=\"padding:14px 18px;color:" + MUTED + ";font-size:13px;\">Credits remaining</td>"
                + "<td style=\"padding:14px 18px;font-size:28px;font-weight:700;color:" + ACCENT + ";\">" + safeCredits + "</td>"
                + "</tr></table>"
                + "<p style=\"margin:0 0 16px;\">Recharge now to avoid any interruption in your learning.</p>"
                + "<a href=\"" + safeUrl + "\" style=\"display:inline-block;background:" + BUTTON + ";color:" + BUTTON_TEXT + ";"
                + "text-decoration:none;font-weight:700;padding:12px 18px;border-radius:8px;\">Recharge credits</a>"
                + "</td></tr>"
                + "<tr><td colspan=\"2\" style=\"padding:0 24px 20px;color:" + MUTED + ";font-size:12px;\">Skillama · hello@skillama.co.in</td>"
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

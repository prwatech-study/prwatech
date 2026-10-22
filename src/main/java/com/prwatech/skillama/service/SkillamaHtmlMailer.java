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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Every Skillama email, learner and team, from hello@skillama.co.in.
 * Prwatech product mail does not use this sender.
 */
@Service
@Slf4j
public class SkillamaHtmlMailer {

    private static final String PAGE = "#f4f6f8";
    private static final String CARD = "#0b1018";
    private static final String ACCENT = "#34d399";
    private static final String CYAN = "#22d3ee";
    private static final String TEXT = "#e5e7eb";
    private static final String MUTED = "#94a3b8";
    private static final String BUTTON = "#10b981";
    private static final String BUTTON_TEXT = "#042f2e";
    private static final Pattern URL = Pattern.compile("https?://\\S+");

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

    /** Any Skillama message, from hello@skillama.co.in, with a readable HTML body. */
    public MailSendResult sendMessage(String email, String subject, String plain) {
        String safeSubject = subject == null || subject.isBlank() ? "Skillama" : subject;
        String safePlain = plain == null ? "" : plain;
        return send(email, safeSubject, safePlain, learnerMessageHtml(safeSubject, safePlain));
    }

    static String verificationPlain(String code, int expiryMinutes) {
        return code + "\n\n"
                + "Your Skillama verification code is " + code + ".\n\n"
                + "This code expires in " + expiryMinutes + " minutes.\n"
                + "If you did not ask for this code, you can ignore this email.\n";
    }

    static String verificationHtml(String code, int expiryMinutes) {
        String safeCode = escape(code);
        return "<!DOCTYPE html><html><body bgcolor=\"" + PAGE + "\" style=\"margin:0;background:" + PAGE + ";font-family:Arial,Helvetica,sans-serif;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" bgcolor=\"" + PAGE + "\" style=\"background:" + PAGE + ";padding:24px 12px;\">"
                + "<tr><td align=\"center\" bgcolor=\"" + PAGE + "\">"
                + "<table role=\"presentation\" width=\"100%\" bgcolor=\"" + CARD + "\" style=\"max-width:480px;background:" + CARD + ";border-radius:12px;overflow:hidden;\">"
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
                + expiryMinutes + " minutes. If you did not ask for it, you can ignore this email. "
                + "Skillama will never ask you to share this code.</p>"
                + "</td></tr></table></td></tr></table></body></html>";
    }

    static String lowCreditPlain(String name, String creditsLeft, String rechargeUrl) {
        return "Hi " + displayName(name) + ",\n\n"
                + "You have low AI credits remaining. Only " + creditsLeft + " credits left.\n\n"
                + "Recharge now so learning is not interrupted:\n"
                + rechargeUrl + "\n";
    }

    static String learnerMessageHtml(String subject, String plain) {
        String safeSubject = escape(subject == null ? "Skillama" : subject);
        String preview = escape(previewText(plain));
        return "<!DOCTYPE html><html><body bgcolor=\"" + PAGE + "\" style=\"margin:0;background:" + PAGE + ";font-family:Arial,Helvetica,sans-serif;\">"
                + "<div style=\"display:none;max-height:0;overflow:hidden;color:" + PAGE + ";\">" + preview + "</div>"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" bgcolor=\"" + PAGE + "\" style=\"background:" + PAGE + ";padding:24px 12px;\">"
                + "<tr><td align=\"center\" bgcolor=\"" + PAGE + "\">"
                + "<table role=\"presentation\" width=\"100%\" bgcolor=\"" + CARD + "\" style=\"max-width:520px;background:" + CARD + ";border-radius:12px;overflow:hidden;\">"
                + "<tr><td style=\"height:4px;background:" + ACCENT + ";font-size:0;line-height:0;\">&nbsp;</td>"
                + "<td style=\"height:4px;background:" + CYAN + ";font-size:0;line-height:0;\">&nbsp;</td></tr>"
                + "<tr><td colspan=\"2\" style=\"padding:24px 24px 8px;color:" + TEXT + ";font-size:15px;line-height:1.5;\">"
                + "<div style=\"color:" + ACCENT + ";font-size:13px;font-weight:700;letter-spacing:0.08em;\">SKILLAMA</div>"
                + "<div style=\"color:#ffffff;font-size:22px;font-weight:700;padding:8px 0 16px;\">" + safeSubject + "</div>"
                + formatPlainAsHtml(plain)
                + "</td></tr>"
                + "<tr><td colspan=\"2\" style=\"padding:8px 24px 20px;color:" + MUTED + ";font-size:12px;\">"
                + "Skillama · hello@skillama.co.in</td>"
                + "</tr></table></td></tr></table></body></html>";
    }

    /** Turns blank-line paragraphs, bullet lines, and a lone URL into email-safe HTML. */
    static String formatPlainAsHtml(String plain) {
        if (plain == null || plain.isBlank()) {
            return "";
        }
        StringBuilder html = new StringBuilder();
        for (String block : plain.replace("\r\n", "\n").split("\n\\s*\n")) {
            String trimmed = block.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] lines = trimmed.split("\n");
            if (isBulletList(lines)) {
                html.append("<ul style=\"margin:0 0 16px;padding-left:18px;color:").append(TEXT).append(";\">");
                for (String line : lines) {
                    String item = stripBullet(line.trim());
                    if (item.isEmpty()) {
                        continue;
                    }
                    html.append("<li style=\"margin:0 0 6px;\">").append(linkify(escape(item))).append("</li>");
                }
                html.append("</ul>");
            } else if (lines.length == 1 && isLoneUrl(lines[0].trim())) {
                String url = stripTrailingPunctuation(lines[0].trim());
                String label = url.contains("skillama") ? "Open Skillama" : url;
                html.append("<p style=\"margin:0 0 16px;\"><a href=\"").append(escape(url))
                        .append("\" style=\"display:inline-block;background:").append(BUTTON)
                        .append(";color:").append(BUTTON_TEXT)
                        .append(";text-decoration:none;font-weight:700;padding:12px 18px;border-radius:8px;\">")
                        .append(escape(label)).append("</a></p>");
            } else {
                html.append("<p style=\"margin:0 0 16px;color:").append(TEXT).append(";\">");
                for (int i = 0; i < lines.length; i++) {
                    if (i > 0) {
                        html.append("<br>");
                    }
                    html.append(linkify(escape(lines[i].trim())));
                }
                html.append("</p>");
            }
        }
        return html.toString();
    }

    static String lowCreditHtml(String name, String creditsLeft, String rechargeUrl) {
        String safeName = escape(displayName(name));
        String safeCredits = escape(creditsLeft);
        String safeUrl = escape(rechargeUrl);
        return "<!DOCTYPE html><html><body bgcolor=\"" + PAGE + "\" style=\"margin:0;background:" + PAGE + ";font-family:Arial,Helvetica,sans-serif;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" bgcolor=\"" + PAGE + "\" style=\"background:" + PAGE + ";padding:24px 12px;\">"
                + "<tr><td align=\"center\" bgcolor=\"" + PAGE + "\">"
                + "<table role=\"presentation\" width=\"100%\" bgcolor=\"" + CARD + "\" style=\"max-width:520px;background:" + CARD + ";border-radius:12px;overflow:hidden;\">"
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

    private static String previewText(String plain) {
        if (plain == null) {
            return "";
        }
        for (String line : plain.replace("\r\n", "\n").split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !isLoneUrl(trimmed)) {
                return trimmed.length() > 140 ? trimmed.substring(0, 140) : trimmed;
            }
        }
        return "";
    }

    private static boolean isBulletList(String[] lines) {
        boolean any = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (!trimmed.startsWith("• ") && !trimmed.startsWith("- ") && !trimmed.startsWith("* ")) {
                return false;
            }
            any = true;
        }
        return any;
    }

    private static String stripBullet(String line) {
        if (line.startsWith("• ") || line.startsWith("- ") || line.startsWith("* ")) {
            return line.substring(2).trim();
        }
        return line;
    }

    private static boolean isLoneUrl(String line) {
        return URL.matcher(stripTrailingPunctuation(line)).matches();
    }

    private static String stripTrailingPunctuation(String value) {
        return value.replaceAll("[.,;:)]+$", "");
    }

    private static String linkify(String escaped) {
        Matcher matcher = URL.matcher(escaped);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String url = stripTrailingPunctuation(matcher.group());
            String tail = matcher.group().substring(url.length());
            matcher.appendReplacement(out, Matcher.quoteReplacement(
                    "<a href=\"" + url + "\" style=\"color:" + ACCENT + ";\">" + url + "</a>" + tail));
        }
        matcher.appendTail(out);
        return out.toString();
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

package com.prwatech.skillama.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prwatech.skillama.dto.IssueReportResponseDTO;
import com.prwatech.skillama.notification.NotificationEventType;
import com.prwatech.skillama.dto.ReportIssueRequestDTO;
import com.prwatech.skillama.model.IssueReport;
import com.prwatech.skillama.repository.IssueReportRepository;
import javax.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class IssueReportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(IssueReportService.class);
    private final IssueReportRepository issueReportRepository;
    private final SkillamaHtmlMailer skillamaHtmlMailer;
    private final ObjectMapper objectMapper;
    private final NotificationSettingsService notificationSettingsService;

    /** Admin triage: paginated issue reports, newest first, optionally by status. */
    public Page<IssueReport> getIssuesForAdmin(String status, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                size <= 0 ? 50 : size,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        if (StringUtils.hasText(status) && !"all".equalsIgnoreCase(status)) {
            return issueReportRepository.findByStatus(status.trim().toUpperCase(), pageable);
        }
        return issueReportRepository.findAll(pageable);
    }

    /** Admin triage: update the workflow status of a reported issue. */
    public IssueReport updateStatus(String id, String status) {
        if (!StringUtils.hasText(status)) {
            throw new IllegalArgumentException("status is required");
        }
        IssueReport issue = issueReportRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Issue report not found: " + id));
        String previous = issue.getStatus();
        String next = status.trim().toUpperCase();
        issue.setStatus(next);
        IssueReport saved = issueReportRepository.save(issue);

        // Close the loop with the reporter when the issue is resolved (only on the
        // transition into RESOLVED, and only if we have their email).
        if ("RESOLVED".equals(next) && !"RESOLVED".equals(previous)) {
            sendResolvedEmailToReporter(saved);
        }
        return saved;
    }

    private void sendResolvedEmailToReporter(IssueReport report) {
        String to = report.getReporterEmail();
        if (!StringUtils.hasText(to)) {
            return; // anonymous / guest report — no reporter to notify
        }
        try {
            String ref = report.getId();
            String reported = StringUtils.hasText(report.getUserDescription())
                    ? report.getUserDescription().trim()
                    : nullToDash(report.getIssueCategory());
            String subject = "We fixed the issue you reported";
            String message = "Hi,\n\n"
                    + "The problem you reported on Skillama is fixed.\n\n"
                    + "What you told us\n"
                    + reported + "\n\n"
                    + "Reference: " + ref + "\n\n"
                    + "If it is still happening, report it again from the same screen and we will take another look.\n\n"
                    + "— Skillama";
            SkillamaHtmlMailer.MailSendResult result =
                    skillamaHtmlMailer.sendMessage(to.trim(), subject, message);
            if (!result.isDelivered()) {
                LOGGER.warn("Resolved email for issue {} was not sent: {}", ref, result.getError());
                return;
            }
            LOGGER.info("Resolved-notification email sent to reporter for issue {}", ref);
        } catch (Exception e) {
            LOGGER.error("Failed to send resolved email to reporter for issue {}", report.getId(), e);
        }
    }

    public IssueReportResponseDTO submit(ReportIssueRequestDTO request, HttpServletRequest httpRequest) {
        if (request == null || !StringUtils.hasText(request.getUserDescription())) {
            throw new IllegalArgumentException("userDescription is required");
        }

        String ua = httpRequest != null ? httpRequest.getHeader("User-Agent") : null;
        Map<String, Object> mergedTech = new LinkedHashMap<>();
        if (request.getClientTechnicalDetails() != null) {
            mergedTech.putAll(request.getClientTechnicalDetails());
        }
        mergedTech.put("httpUserAgentHeader", ua);

        String rawJson;
        try {
            rawJson = objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            rawJson = "{\"error\":\"failed_to_serialize_request\"}";
        }

        String human = buildHumanReadable(request, ua);
        String stack = buildStackTraceStyle(request, mergedTech);

        IssueReport entity = new IssueReport();
        entity.setIssueCategory(trimToNull(request.getIssueCategory()));
        entity.setUserDescription(request.getUserDescription().trim());
        entity.setHumanReadableSummary(human);
        entity.setStackTraceFormatSummary(stack);
        entity.setRawClientPayloadJson(rawJson);
        entity.setReporterUserId(trimToNull(request.getReporterUserId()));
        entity.setReporterEmail(trimToNull(request.getReporterEmail()));
        entity.setClientPagePath(trimToNull(request.getClientPagePath()));
        entity.setServerRecordedUserAgent(trimToNull(ua));
        if (request.getAttachmentUrls() != null) {
            entity.setAttachmentUrls(request.getAttachmentUrls());
        }

        IssueReport saved = issueReportRepository.save(entity);
        sendTeamEmail(saved);
        return IssueReportResponseDTO.builder()
                .id(saved.getId())
                .message("Thank you — your report was recorded. Our team will review it.")
                .build();
    }

    private static String trimToNull(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private String buildHumanReadable(ReportIssueRequestDTO r, String userAgentHeader) {
        String ts = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        StringBuilder sb = new StringBuilder();
        sb.append("Skillama — Issue report (human-readable)\n");
        sb.append("==========================================\n");
        sb.append("Recorded (UTC): ").append(ts).append("\n");
        sb.append("Category: ").append(nullToDash(r.getIssueCategory())).append("\n");
        sb.append("Reporter user id: ").append(nullToDash(r.getReporterUserId())).append("\n");
        sb.append("Reporter email: ").append(nullToDash(r.getReporterEmail())).append("\n");
        sb.append("Client page path: ").append(nullToDash(r.getClientPagePath())).append("\n");
        sb.append("HTTP User-Agent (server): ").append(nullToDash(userAgentHeader)).append("\n");
        sb.append("\n--- Course / lecture context ---\n");
        sb.append("Course id: ").append(nullToDash(r.getCourseId())).append("\n");
        sb.append("Course name: ").append(nullToDash(r.getCourseName())).append("\n");
        sb.append("Module: ").append(nullToDash(r.getModuleName())).append("\n");
        sb.append("Lecture / topic label: ").append(nullToDash(r.getLectureLabel())).append("\n");
        sb.append("\n--- What the user reported ---\n");
        sb.append(r.getUserDescription().trim()).append("\n");
        sb.append("\n--- Next steps for support ---\n");
        sb.append("1) Open admin curriculum for the course and verify scriptText for practical topics.\n");
        sb.append("2) Cross-check text-to-audio logs if category mentions audio.\n");
        sb.append("3) Reply via existing review/feedback workflow if user left contact info.\n");
        return sb.toString();
    }

    private String buildStackTraceStyle(ReportIssueRequestDTO r, Map<String, Object> mergedTech) {
        StringBuilder sb = new StringBuilder();
        sb.append("com.skillama.support.IssueReportContext\n");
        appendFrame(sb, "issueCategory", r.getIssueCategory());
        appendFrame(sb, "reporterUserId", r.getReporterUserId());
        appendFrame(sb, "reporterEmail", r.getReporterEmail());
        appendFrame(sb, "clientPagePath", r.getClientPagePath());
        appendFrame(sb, "courseId", r.getCourseId());
        appendFrame(sb, "courseName", r.getCourseName());
        appendFrame(sb, "moduleName", r.getModuleName());
        appendFrame(sb, "lectureLabel", r.getLectureLabel());
        appendFrame(sb, "userDescription.length", r.getUserDescription() != null ? r.getUserDescription().length() : 0);
        sb.append("Caused by: client.technicalDetails (merged)\n");
        try {
            String pretty = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(mergedTech);
            for (String line : pretty.split("\\R")) {
                sb.append("    ").append(line).append("\n");
            }
        } catch (JsonProcessingException e) {
            sb.append("    <serialization_error: ").append(e.getMessage()).append(">\n");
        }
        sb.append("... ").append(mergedTech.size()).append(" technical detail entries\n");
        return sb.toString();
    }

    private static void appendFrame(StringBuilder sb, String key, Object value) {
        sb.append("  at ").append(key).append(": ").append(value == null ? "null" : String.valueOf(value)).append("\n");
    }

    private static String nullToDash(String s) {
        return StringUtils.hasText(s) ? s : "—";
    }

    private static String summaryLine(String summary, String label) {
        if (summary == null) {
            return "—";
        }
        for (String line : summary.split("\n")) {
            if (line.startsWith(label)) {
                String value = line.substring(label.length()).trim();
                return value.isEmpty() ? "—" : value;
            }
        }
        return "—";
    }

    private static String friendlyCategory(String category) {
        if (!StringUtils.hasText(category)) {
            return "General";
        }
        String[] parts = category.trim().toLowerCase(java.util.Locale.ROOT).split("_");
        StringBuilder label = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (label.length() > 0) {
                label.append(' ');
            }
            label.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                label.append(part.substring(1));
            }
        }
        return label.length() == 0 ? "General" : label.toString();
    }

    private void sendTeamEmail(IssueReport report) {
        try {
            String subject = "Skillama issue — " + friendlyCategory(report.getIssueCategory());
            String message = "A learner reported a problem. The short version is below. "
                    + "Logs and the raw payload are in Admin → Issue reports.\n\n"
                    + "What they said\n"
                    + nullToDash(report.getUserDescription()) + "\n\n"
                    + "Where\n"
                    + "Page: " + nullToDash(report.getClientPagePath()) + "\n"
                    + "Course: " + summaryLine(report.getHumanReadableSummary(), "Course name: ") + "\n"
                    + "Module: " + summaryLine(report.getHumanReadableSummary(), "Module: ") + "\n"
                    + "Lecture: " + summaryLine(report.getHumanReadableSummary(), "Lecture / topic label: ") + "\n"
                    + "Category: " + friendlyCategory(report.getIssueCategory()) + "\n\n"
                    + "Reporter\n"
                    + "Email: " + nullToDash(report.getReporterEmail()) + "\n"
                    + "User id: " + nullToDash(report.getReporterUserId()) + "\n\n"
                    + "Reference: " + report.getId() + "\n\n"
                    + "— Skillama";

            notificationSettingsService.sendTeamNotification(
                    NotificationEventType.ISSUE_REPORT, subject, message);
            LOGGER.info("Issue report notification sent for {}", report.getId());
        } catch (Exception e) {
            LOGGER.error("Failed to send issue report email for {}", report.getId(), e);
        }
    }
}

package com.prwatech.skillama.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prwatech.common.dto.EmailSendDto;
import com.prwatech.common.service.impl.EmailServiceImpl;
import com.prwatech.skillama.SkillamaNotificationEmails;
import com.prwatech.skillama.dto.IssueReportResponseDTO;
import com.prwatech.skillama.dto.ReportIssueRequestDTO;
import com.prwatech.skillama.model.IssueReport;
import com.prwatech.skillama.repository.IssueReportRepository;
import javax.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final EmailServiceImpl emailService;
    private final ObjectMapper objectMapper;

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

    private void sendTeamEmail(IssueReport report) {
        try {
            String subject = "Skillama issue report — " + nullToDash(report.getIssueCategory()) + " — " + report.getId();
            String message = report.getHumanReadableSummary()
                    + "\n\n----- stack-trace style (duplicate) -----\n\n"
                    + report.getStackTraceFormatSummary()
                    + "\n\n----- raw client JSON -----\n\n"
                    + (report.getRawClientPayloadJson() != null ? report.getRawClientPayloadJson() : "");

            for (String teamEmail : SkillamaNotificationEmails.TEAM_INBOXES) {
                emailService.sendEmail(new EmailSendDto(teamEmail, subject, message));
            }
            LOGGER.info("Issue report notification sent for {}", report.getId());
        } catch (Exception e) {
            LOGGER.error("Failed to send issue report email for {}", report.getId(), e);
        }
    }
}

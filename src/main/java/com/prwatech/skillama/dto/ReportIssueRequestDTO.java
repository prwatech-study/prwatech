package com.prwatech.skillama.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client body for {@code POST /skillama/issues/report}.
 */
@Data
public class ReportIssueRequestDTO {

    /** e.g. PRACTICAL_SCRIPT_MISSING, TEXT_TO_AUDIO, CURRICULUM, OTHER */
    private String issueCategory;
    /** Required free-text from the learner. */
    private String userDescription;

    private String courseId;
    private String courseName;
    private String moduleName;
    private String lectureLabel;
    private String clientPagePath;

    private String reporterUserId;
    private String reporterEmail;

    /** Public S3 URLs of attachments uploaded via POST /skillama/issues/attachments. */
    private List<String> attachmentUrls;

    /**
     * Optional structured client context (API errors, Redux snapshot ids, etc.).
     */
    private Map<String, Object> clientTechnicalDetails = new LinkedHashMap<>();
}

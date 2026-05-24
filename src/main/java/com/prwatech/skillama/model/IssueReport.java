package com.prwatech.skillama.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import com.prwatech.skillama.util.IndiaTime;

/**
 * User-submitted technical / content issue (separate from star-rating {@code Review}).
 */
@Data
@Document(collection = "issue_reports")
public class IssueReport {

    @Id
    private String id;
    private LocalDateTime createdAt = IndiaTime.now();

    private String issueCategory;
    private String userDescription;

    /**
     * Plain-language summary for support triage.
     */
    private String humanReadableSummary;

    /**
     * Pseudo stack-trace style block for engineers (includes context keys one per line).
     */
    private String stackTraceFormatSummary;

    /** Original client payload (JSON string) for forensics. */
    private String rawClientPayloadJson;

    private String reporterUserId;
    private String reporterEmail;

    private String serverRecordedUserAgent;
    private String clientPagePath;

    private String status = "OPEN";
}

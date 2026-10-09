package com.prwatech.skillama.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.prwatech.skillama.model.CertificationBankBuildStatus;
import com.prwatech.skillama.model.CertificationExamMeta;
import com.prwatech.skillama.model.CertificationTier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GlobalCertificationExamDTO {
    private String id;
    private String provider;
    private CertificationTier tier;
    private String name;
    private String guidelinesUrl;
    private String description;
    private boolean active;
    /** True when a guidelines snapshot has been stored. */
    private boolean guidelinesReady;
    private CertificationExamMeta parsedMeta;

    private CertificationBankBuildStatus bankStatus;
    private Integer bankVersion;
    private Integer bankTargetSize;
    private Integer bankQuestionCount;
    /** Progress of an in-flight rebuild; null when not RUNNING. */
    private Integer bankBuildQuestionCount;
    private int bankMultiplier;
    private boolean rebuildAllowed;
    /** When a complete bank may be rebuilt again; null if no 24h cooldown is active. */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime rebuildAvailableAt;
    /** True when learners can assemble an exam from the current bank. */
    private boolean bankReady;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime bankBuildStartedAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime bankBuildFinishedAt;

    private String bankBuildTriggeredBy;
    private String bankBuildError;

    /** Lifetime rate-card cost (USD) for building this cert's question bank. */
    private Double bankLifetimeCostUsd;
    /** Most recent rebuild window cost (USD). */
    private Double bankLastRebuildCostUsd;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    private String createdBy;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;

    private String updatedBy;
}

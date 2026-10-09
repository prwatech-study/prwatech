package com.prwatech.skillama.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Admin-managed global certification examination catalog entry (GCP first; AWS/Azure later).
 * Guidelines are fetched from {@link #guidelinesUrl} and cached for AI exam generation.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "global_certification_exams")
@CompoundIndex(name = "provider_name_unique", def = "{'provider': 1, 'nameKey': 1}", unique = true)
public class GlobalCertificationExam {

    @Id
    private String id;

    /** Cloud provider code, e.g. GCP. */
    @Indexed
    private String provider;

    private CertificationTier tier;

    private String name;

    /** Lowercased collapsed name for duplicate checks within a provider. */
    private String nameKey;

    /** Official certification guidelines page URL. */
    private String guidelinesUrl;

    private String description;

    @Builder.Default
    private boolean active = true;

    /** Plain-text snapshot of the guidelines page. */
    private String guidelinesSnapshot;

    private CertificationExamMeta parsedMeta;

    /** Question bank build lifecycle (5× exam size target). */
    @Builder.Default
    private CertificationBankBuildStatus bankStatus = CertificationBankBuildStatus.IDLE;

    /** Latest READY bank version; learners assemble from this. */
    @Builder.Default
    private Integer bankVersion = 0;

    /** Version currently being built (set while RUNNING; promoted to bankVersion on success). */
    private Integer bankBuildVersion;

    /** Target bank size = exam question count × multiplier (default 5). */
    private Integer bankTargetSize;

    /** Active questions in the current bank version. */
    private Integer bankQuestionCount;

    private LocalDateTime bankBuildStartedAt;
    private LocalDateTime bankBuildFinishedAt;
    private String bankBuildTriggeredBy;
    private String bankBuildError;

    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
}

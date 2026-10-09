package com.prwatech.skillama.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.prwatech.skillama.model.CertificationBankBuildStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CertificationBankStatusDTO {
    private String certificationExamId;
    private CertificationBankBuildStatus bankStatus;
    private Integer bankVersion;
    private Integer bankTargetSize;
    private Integer bankQuestionCount;
    /** Progress of an in-flight rebuild; null when not RUNNING. */
    private Integer bankBuildQuestionCount;
    private int bankMultiplier;
    private boolean rebuildAllowed;
    private boolean bankReady;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime bankBuildStartedAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime bankBuildFinishedAt;

    private String bankBuildTriggeredBy;
    private String bankBuildError;
}

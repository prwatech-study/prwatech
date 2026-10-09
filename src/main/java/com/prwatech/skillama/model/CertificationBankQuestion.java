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
import java.util.ArrayList;
import java.util.List;

/**
 * One question in a global certification exam question bank.
 * Learners never hit this collection directly — papers are assembled into {@link ExamSession}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "certification_bank_questions")
@CompoundIndex(name = "cert_version_active", def = "{'certificationExamId': 1, 'bankVersion': 1, 'active': 1}")
public class CertificationBankQuestion {

    @Id
    private String id;

    @Indexed
    private String certificationExamId;

    /** Increments on each successful rebuild; only the latest active version is served. */
    private int bankVersion;

    @Builder.Default
    private boolean active = true;

    private Integer questionId;
    private String question;
    /** Lowercased collapsed stem for de-dupe within a bank version. */
    @Indexed
    private String questionNorm;
    private ExamQuestionType questionType;
    @Builder.Default
    private List<ExamSession.ExamOption> options = new ArrayList<>();
    private String correctKey;
    @Builder.Default
    private List<String> correctKeys = new ArrayList<>();
    private String explanation;
    private String domain;

    private LocalDateTime createdAt;
}

package com.prwatech.skillama.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One Tester's evaluation of a single topic (submodule), answered against the active
 * {@link EvaluationQuestion} bank at submission time. Question text/category are snapshotted
 * onto each answer so a response stays readable even if the question bank changes later.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "tester_evaluation_responses")
public class TesterEvaluationResponse {
    @Id
    private String id;

    @Indexed
    private String testerId;
    private String testerName;
    private String testerEmail;

    @Indexed
    private String courseId;
    private String courseNameSnapshot;

    private String curriculumModuleId;
    private String moduleNameSnapshot;

    @Indexed
    private String submoduleId;
    private String submoduleLabelSnapshot;

    private List<EvaluationAnswer> answers;

    /** Derived: true if any answer is NO — drives the review dashboard's flagged filter. */
    @Indexed
    private boolean hasFlaggedIssues;

    private LocalDateTime submittedAt;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvaluationAnswer {
        private String questionId;
        private String questionTextSnapshot;
        private EvaluationQuestion.Category category;
        private Answer answer;
        /** Required when answer is NO; null/blank otherwise. */
        private String followUpComment;
    }

    public enum Answer {
        YES, NO
    }
}

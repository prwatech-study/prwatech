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
import java.util.ArrayList;
import java.util.List;

/** A graded AI Exam submission. Pure self-assessment — no progression side effects. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "exam_attempts")
public class ExamAttempt {

    @Id
    private String id;

    @Indexed
    private String userId;

    @Indexed
    private String courseId;

    private String moduleId;
    private String topic;
    /** Real FK to CourseCurriculum, set only when the picker resolved a real module. */
    private String curriculumModuleId;
    /** Real FK to CourseCurriculum.Submodule, set only when the picker resolved a real submodule. */
    private String submoduleId;
    private ExamDifficulty difficulty;
    private ExamType examType;

    @Indexed
    private String examSessionId;

    private Integer score;
    private Integer maxScore;
    private Double percentage;

    @Builder.Default
    private List<AnswerRecord> answers = new ArrayList<>();

    /** Server-computed (session.startedAt -> now at submission) — never trusted from the client. */
    private Integer timeSpentSeconds;
    private Boolean overTimeLimit;
    private LocalDateTime submittedAt;

    /** AI-generated once at submission and stored — never regenerated on later views. */
    private String overallFeedback;
    private String recommendationText;

    /** Client-reported focus violations (tab switches / fullscreen exits) — informational only. */
    private Integer violationCount;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnswerRecord {
        private Integer questionId;
        private String questionText;
        private String selectedKey;
        private String correctKey;
        private Boolean isCorrect;
        private String explanation;
        private List<ExamSession.ExamOption> options;
    }
}

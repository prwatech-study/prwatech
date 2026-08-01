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

/**
 * An AI Exam attempt-in-progress. Mirrors the hardened Module Quiz pattern: questions
 * (and their answer key) are generated server-side and never round-trip through the
 * browser, and the exam clock (startedAt/timeLimitSeconds) is server-owned so elapsed
 * time can never be spoofed by the client. Unlike Module Quiz, an AI Exam is pure
 * self-assessment — passing/failing has no effect on course progression.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "exam_sessions")
public class ExamSession {

    @Id
    private String id;

    @Indexed(unique = true)
    private String examSessionId;

    @Indexed
    private String userId;

    @Indexed
    private String courseId;

    /** Null for a course/topic-wide exam (not scoped to a single module). */
    private String moduleId;
    private String topic;

    private ExamDifficulty difficulty;
    private ExamType examType;
    private String examTitle;

    @Builder.Default
    private List<ExamQuestion> questions = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private Integer timeLimitSeconds;
    private LocalDateTime expiresAt;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExamQuestion {
        private Integer id;
        private String question;
        private List<ExamOption> options;
        private String correctKey;
        private String explanation;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExamOption {
        private String key;
        private String text;
    }
}

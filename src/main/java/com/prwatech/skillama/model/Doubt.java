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
 * A single AI Mentor doubt: one user question and its AI Mentor conversation
 * (initial answer plus any follow-up nudges), scoped to a course/module/lesson.
 * Each new question a user asks creates its own Doubt — this is the countable
 * unit behind admin/analytics stats like "34 doubts asked".
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "doubts")
public class Doubt {
    @Id
    private String id;

    @Indexed
    private String userId;

    @Indexed
    private String courseId;

    private String moduleId;
    private String lessonId;

    @Indexed
    private DoubtStatus status;

    @Builder.Default
    private List<DoubtMessage> messages = new ArrayList<>();

    @Indexed
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime resolvedAt;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DoubtMessage {
        private String id;
        private Sender sender;
        private String content;

        /**
         * Which nudge produced this message — e.g. EXPLAIN_MORE, EXPLAIN_LIKE_BEGINNER,
         * REAL_LIFE_EXAMPLE, REGENERATE. Null for the original question/first answer
         * and for free-form follow-up questions.
         */
        private String nudgeType;

        /** Feedback on an AI message; null = no feedback given yet. */
        private Boolean helpful;

        private LocalDateTime timestamp;
    }

    public enum Sender {
        USER, AI
    }
}

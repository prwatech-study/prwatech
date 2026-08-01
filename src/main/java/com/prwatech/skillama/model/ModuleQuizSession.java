package com.prwatech.skillama.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "module_quiz_sessions")
public class ModuleQuizSession {

    @Id
    private String id;

    @Indexed(unique = true)
    private String quizSessionId;

    @Indexed
    private String userId;

    @Indexed
    private String guestSessionId;

    @Indexed
    private String courseId;

    private String moduleName;
    private String quizTitle;

    @Builder.Default
    private List<QuizQuestion> questions = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;

    /** Server-owned exam clock — elapsed time is always computed from this, never from the client. */
    private LocalDateTime startedAt;
    private Integer timeLimitSeconds;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuizQuestion {
        private Integer id;
        private String question;
        private List<QuizOption> options;
        private String correctKey;
        private String explanation;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuizOption {
        private String key;
        private String text;
    }
}

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
@Document(collection = "module_quiz_attempts")
public class ModuleQuizAttempt {

    @Id
    private String id;

    @Indexed
    private String userId;

    @Indexed
    private String guestSessionId;

    @Indexed
    private String courseId;

    private String moduleName;

    @Indexed
    private String quizSessionId;

    private Integer attemptNumber;
    private Integer score;
    private Integer maxScore;
    private Double percentage;
    private Boolean passed;

    @Builder.Default
    private List<AnswerRecord> answers = new ArrayList<>();

    private Integer timeSpentSeconds;
    private LocalDateTime submittedAt;

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
        private List<ModuleQuizSession.QuizOption> options;
    }
}

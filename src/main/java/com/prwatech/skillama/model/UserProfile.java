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
@Document(collection = "user_profiles")
public class UserProfile {
    @Id
    private String id;
    
    @Indexed
    private String userId;              // null for non-logged-in users
    
    @Indexed(unique = true)
    private String sessionId;           // Unique session identifier (cookie/device)
    
    @Builder.Default
    private Boolean isGuest = Boolean.TRUE;  // true for non-logged-in users
    
    // Course Access
    @Builder.Default
    private List<String> accessibleCourses = new ArrayList<>();  // Course IDs user can access
    
    private String currentCourseId;     // Currently active course
    
    // Progress Tracking
    @Builder.Default
    private List<CompletedLecture> completedLectures = new ArrayList<>();
    
    @Builder.Default
    private List<InProgressLecture> inProgressLectures = new ArrayList<>();
    
    @Builder.Default
    private List<String> lockedLectures = new ArrayList<>();      // Lecture labels that are locked
    
    @Builder.Default
    private List<String> unlockedLectures = new ArrayList<>();    // Lecture labels that are unlocked
    
    // Chat/Questions Tracking
    @Builder.Default
    private Integer totalQuestionsAsked = 0;
    
    @Builder.Default
    private List<ChatInteraction> chatInteractions = new ArrayList<>();

    @Builder.Default
    private List<PassedModuleQuiz> passedModuleQuizzes = new ArrayList<>();

    /** Modules the learner skipped after failing the quiz (next content unlocked; quiz still pending). */
    @Builder.Default
    private List<SkippedModuleQuiz> skippedModuleQuizzes = new ArrayList<>();

    /**
     * Consecutive AI quiz-generation failures per module (the AI never returned usable
     * questions, so no scored attempt was even possible). Counted toward the same
     * skip-after-N-tries threshold as real failed attempts, and reset once a session
     * generates successfully.
     */
    @Builder.Default
    private List<QuizGenerationFailure> quizGenerationFailures = new ArrayList<>();

    /**
     * Server-side learner memory (source of truth for logged-in users).
     * Injected into chat/tutor context; audited via updates + chat history.
     */
    @Builder.Default
    private LearnerMemory learnerMemory = new LearnerMemory();

    /** Daily learning streak persisted for email reminders / loss notices. */
    @Builder.Default
    private LearningStreak learningStreak = new LearningStreak();

    /** UI + tutor locale: {@code en} (default) or {@code hi}. Gated by platform hindiTutorEnabled. */
    @Builder.Default
    private String preferredLocale = "en";

    // Metadata
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastActivityAt;
    private LocalDateTime sessionExpiresAt;  // For guest sessions (TTL)
    
    // Nested Classes
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompletedLecture {
        private String lectureLabel;          // e.g., "Introduction to Python"
        private String courseId;
        private String moduleName;
        private LocalDateTime completedAt;
        private Integer timeSpent;            // seconds
        private Integer completionPercentage;  // 0-100, if partially completed
    }
    
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InProgressLecture {
        private String lectureLabel;
        private String courseId;
        private LocalDateTime startedAt;
        private LocalDateTime lastAccessedAt;
        private Integer progressPercentage;    // 0-100
        private Integer timeSpent;            // seconds
    }
    
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatInteraction {
        private String id;
        private String question;             // User's question (text or audio transcript)
        private String answer;               // AI's response text
        private String audioUrl;             // AI's response audio URL
        private LocalDateTime timestamp;
        private String lectureContext;       // Which lecture was active when asked
        private String courseId;
        private String questionType;         // "text" or "audio"
        /** Milliseconds from question send until AI reply was received. */
        private Long responseTimeMs;
        /** Seconds the learner spent speaking (voice questions). */
        private Integer userSpeakDurationSeconds;
        /** Seconds of AI answer audio played (when available). */
        private Integer answerAudioDurationSeconds;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PassedModuleQuiz {
        private String courseId;
        private String moduleName;
        private LocalDateTime passedAt;
        private Integer bestScore;
        private Integer maxScore;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkippedModuleQuiz {
        private String courseId;
        private String moduleName;
        private LocalDateTime skippedAt;
        private Integer attemptCountAtSkip;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuizGenerationFailure {
        private String courseId;
        private String moduleName;
        private Integer failureCount;
        private LocalDateTime lastFailedAt;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LearnerMemory {
        private String preferredPace;
        @Builder.Default
        private List<String> facts = new ArrayList<>();
        @Builder.Default
        private List<String> weakTopics = new ArrayList<>();
        /** Optional rolling summary derived from recent chat (kept short). */
        private String summary;
        private LocalDateTime updatedAt;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LearningStreak {
        @Builder.Default
        private Integer currentStreak = 0;
        @Builder.Default
        private Integer longestStreak = 0;
        /** India calendar day yyyy-MM-dd of last learning activity. */
        private String lastActiveDate;
        @Builder.Default
        private Integer totalActiveDays = 0;
        private LocalDateTime lastReminderSentAt;
        private LocalDateTime lastLostEmailSentAt;
        /** Streak length captured when a loss email was sent (dedupe). */
        private Integer lostNotifiedForStreak;
    }
}


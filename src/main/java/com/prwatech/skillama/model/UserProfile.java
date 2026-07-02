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
}


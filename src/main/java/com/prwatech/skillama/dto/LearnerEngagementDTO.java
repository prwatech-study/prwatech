package com.prwatech.skillama.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class LearnerEngagementDTO {
    private String preferredLocale;
    private boolean hindiTutorAvailable;
    private LearnerMemoryDTO memory;
    private LearningStreakDTO streak;

    @Data
    @Builder
    public static class LearnerMemoryDTO {
        private String preferredPace;
        @Builder.Default
        private List<String> facts = new ArrayList<>();
        @Builder.Default
        private List<String> weakTopics = new ArrayList<>();
        private String summary;
        private LocalDateTime updatedAt;
        /** Compact string ready for AI learner_context. */
        private String contextPrompt;
    }

    @Data
    @Builder
    public static class LearningStreakDTO {
        private int currentStreak;
        private int longestStreak;
        private String lastActiveDate;
        private int totalActiveDays;
        private boolean atRisk;
    }
}

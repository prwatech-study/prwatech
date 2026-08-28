package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemoResetResultDTO {
    private String userId;
    private String email;
    private long deletedDoubts;
    private long deletedQuizAttempts;
    private long deletedQuizSessions;
    private long deletedExamAttempts;
    private long deletedExamSessions;
    private long deletedRecommendationLogs;
    private long deletedAnswerFeedback;
    private int clearedChatInteractions;
    private DemoDashboardSeedResultDTO seedResult;
}

package com.prwatech.skillama.dto;

import com.prwatech.skillama.model.ExamDifficulty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Admin view of a single "AI Recommended Test" suggestion. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminExamRecommendationDTO {
    private String id;
    private String userId;
    private String userName;
    private String userEmail;
    private String courseId;
    private String courseName;
    private ExamDifficulty difficulty;
    private String topic;
    private String reasoning;
    private Integer estimatedMinutes;
    private Integer expectedScorePercent;
    private LocalDateTime createdAt;
}

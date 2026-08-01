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

/**
 * A persisted record of an "AI Recommended Test" suggestion — so a recommendation
 * can be reviewed after the fact, not just billed for token cost.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "exam_recommendation_logs")
public class ExamRecommendationLog {
    @Id
    private String id;

    @Indexed
    private String userId;

    @Indexed
    private String courseId;

    private ExamDifficulty difficulty;
    private String topic;
    private String reasoning;
    private Integer estimatedMinutes;
    private Integer expectedScorePercent;
    private String modelId;

    @Indexed
    private LocalDateTime createdAt;
}

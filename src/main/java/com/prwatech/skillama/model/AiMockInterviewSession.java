package com.prwatech.skillama.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "ai_mock_interview_sessions")
public class AiMockInterviewSession {
    @Id
    private String id;

    @Indexed
    private String userId;
    private String configId;
    private String title;
    private String style;
    private Integer durationMinutes;
    /** IN_PROGRESS or COMPLETED. */
    private String status;
    private Instant startedAt;
    private Instant endsAt;
    private Instant completedAt;
    private Integer durationSeconds;

    @Builder.Default
    private List<InterviewTurn> turns = new ArrayList<>();

    /** Practice feedback is visible to the candidate. Org interview scores are not. */
    private Integer score;
    private String feedback;
    private String feedbackSummary;
}

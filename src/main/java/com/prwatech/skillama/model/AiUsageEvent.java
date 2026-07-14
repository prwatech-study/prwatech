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

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "ai_usage_events")
public class AiUsageEvent {
    @Id
    private String id;

    @Indexed
    private String userId;

    private String sessionId;

    @Indexed
    private String endpoint;

    private String modelId;

    private String courseId;

    private int inputTokens;
    private int outputTokens;
    private int totalTokens;

    private double costUsd;
    private double costInr;

    @Indexed
    private LocalDateTime createdAt;
}

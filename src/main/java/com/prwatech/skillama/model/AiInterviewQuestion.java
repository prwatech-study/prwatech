package com.prwatech.skillama.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "ai_interview_questions")
public class AiInterviewQuestion {
    @Id
    private String id;

    /** Null for the platform bank used when a schedule has no organization. */
    @Indexed
    private String organizationId;

    private String text;

    /** BEHAVIORAL, TECHNICAL, or MIXED. */
    @Indexed
    private String tag;

    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;
}

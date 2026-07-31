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
 * Admin-configurable evaluation question shown to a Tester after each topic.
 * Category groups mirror the three review areas: content accuracy, image relevance, practical correctness.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "evaluation_questions")
public class EvaluationQuestion {
    @Id
    private String id;

    @Indexed
    private Category category;

    private String questionText;

    /** Display order within its category. */
    private Integer order;

    @Builder.Default
    private boolean active = true;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;

    public enum Category {
        CONTENT, IMAGE, PRACTICAL
    }
}

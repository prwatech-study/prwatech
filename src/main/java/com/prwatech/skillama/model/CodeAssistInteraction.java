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
 * One Debug/Code Execution interaction — the code submitted, the AI's output/correction/
 * explanation, and cost metadata. This is the content-level record that previously didn't
 * exist for these two features (only token/cost totals were tracked, and only as a
 * client-reported backup), so admin has no way to see what learners are actually asking.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "code_assist_interactions")
public class CodeAssistInteraction {
    @Id
    private String id;

    @Indexed
    private String userId;

    @Indexed
    private String courseId;

    @Indexed
    private CodeAssistFeature feature;

    private String code;
    private String codeOutput;
    private String correctedCode;
    private String responseText;

    /** True when {@code codeOutput} came from a real sandbox run/verdict rather than ai-tutor hallucination. */
    private Boolean sandboxVerified;

    /** Spoken-explanation audio URL from ai-tutor — never handed to the client directly, see ProxiedAudioDTO. */
    private String audioUrl;

    private String modelId;
    private int inputTokens;
    private int outputTokens;
    private int totalTokens;

    @Indexed
    private LocalDateTime createdAt;
}

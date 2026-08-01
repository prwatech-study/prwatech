package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Submits answers for grading. Elapsed time is NOT accepted from the client —
 * the server computes it from the session's server-owned startedAt.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitModuleQuizAttemptRequestDTO {
    private String sessionId;
    /** questionId (string key) -> selected option key (A/B/C/D) */
    private Map<String, String> answers;
}

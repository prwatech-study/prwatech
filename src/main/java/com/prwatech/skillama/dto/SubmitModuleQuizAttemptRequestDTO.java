package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitModuleQuizAttemptRequestDTO {
    private String sessionId;
    /** questionId (string key) -> selected option key (A/B/C/D) */
    private Map<String, String> answers;
    private Integer timeSpentSeconds;
}

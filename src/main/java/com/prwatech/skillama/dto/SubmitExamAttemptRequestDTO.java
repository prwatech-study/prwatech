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
public class SubmitExamAttemptRequestDTO {
    private String examSessionId;
    /**
     * questionId (string key) -> selected option key (String) or multi-select keys (List/Collection).
     * Jackson accepts both {@code "1":"A"} and {@code "2":["A","C"]}.
     */
    private Map<String, Object> answers;
    /**
     * Focus violations the client recorded during the attempt (tab switches, window blur,
     * fullscreen exits). Client-reported and best-effort only — informational, never
     * affects grading.
     */
    private Integer violationCount;
}

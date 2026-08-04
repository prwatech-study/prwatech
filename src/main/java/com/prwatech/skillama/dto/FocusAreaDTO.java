package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One topic/module the learner has taken TOPIC_WISE/MODULE_WISE exams on,
 * with their average score for it. {@code focusArea} flags areas below the
 * passing threshold — field naming is deliberately neutral ("focus", not
 * "weak") per the non-judgmental framing this feature was built with.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FocusAreaDTO {
    private String label;
    private Double averagePercentage;
    private Integer attemptCount;
    private Boolean focusArea;
    private AreaLinkDTO link;
}

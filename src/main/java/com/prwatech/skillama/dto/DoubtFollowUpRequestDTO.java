package com.prwatech.skillama.dto;

import lombok.Data;

/**
 * A follow-up exchange on an existing doubt — either a nudge template click
 * (nudgeType set, e.g. EXPLAIN_MORE) or a free-form follow-up question
 * (nudgeType null). The backend generates the answer (see DoubtService).
 *
 * <p>{@code question} is what gets persisted as the user's message (e.g. a short nudge
 * label like "Explain More"); {@code query} is the actual text sent to the LLM, which for
 * nudge templates is a longer constructed prompt referencing the original Q&amp;A — the two
 * differ only for nudges. For a free-form follow-up they're the same text.
 */
@Data
public class DoubtFollowUpRequestDTO {
    private String nudgeType;
    private String question;
    private String query;
}

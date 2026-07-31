package com.prwatech.skillama.dto;

import lombok.Data;

/**
 * A follow-up exchange on an existing doubt — either a nudge template click
 * (nudgeType set, e.g. EXPLAIN_MORE) or a free-form follow-up question
 * (nudgeType null, question set). The AI answer is supplied by the caller,
 * same as {@link AskDoubtRequestDTO}.
 */
@Data
public class DoubtFollowUpRequestDTO {
    private String nudgeType;
    private String question;
    private String answer;
}

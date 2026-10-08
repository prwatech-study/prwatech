package com.prwatech.skillama.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Question text copied onto a schedule so later bank edits do not rewrite a live invite. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewSeedQuestion {
    private String id;
    private String text;
    private String tag;
}

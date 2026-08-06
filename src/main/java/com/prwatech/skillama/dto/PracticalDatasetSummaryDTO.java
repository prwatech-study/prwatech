package com.prwatech.skillama.dto;

import lombok.*;

/** Learner-facing view of a practical dataset — the file name and the identifier the AI is given, nothing else. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PracticalDatasetSummaryDTO {
    private String datasetId;
    private String displayName;
}

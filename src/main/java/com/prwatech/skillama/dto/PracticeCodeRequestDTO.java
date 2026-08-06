package com.prwatech.skillama.dto;

import lombok.Data;

@Data
public class PracticeCodeRequestDTO {
    private String query;
    private String codeInstruction;
    private String courseId;
    /** Optional: set when the current lesson has a practical dataset attached, so the
     * generated code references its real filename/columns instead of guessing. */
    private String datasetId;
}

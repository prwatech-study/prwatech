package com.prwatech.skillama.dto;

import lombok.Data;

@Data
public class CodeAssistRequestDTO {
    private String code;
    private String courseId;
    /** Optional — set by the frontend when the current lesson has a practical dataset attached,
     * so Debug/Code-Execution can run against it too, the same way the Practical Exercise tab
     * does, instead of ad-hoc mode with no file access. Ignored for non-Python courses. */
    private String datasetId;
}

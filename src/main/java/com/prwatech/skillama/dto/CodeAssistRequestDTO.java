package com.prwatech.skillama.dto;

import lombok.Data;

@Data
public class CodeAssistRequestDTO {
    private String code;
    private String courseId;
    /** Optional — set by the frontend when the current lesson has a practical dataset attached,
     * so Debug/Code-Execution can run against it too, the same way the Practical Exercise tab
     * does, instead of ad-hoc mode with no file access. Used for the practice sandbox only. */
    private String datasetId;
    /** Optional compiler runtime id (java17, python3.12, …). Ignored when it is not one of the course's runtimes. */
    private String runtime;
    /** Optional standard input for compiler courses. Capped by the sandbox. */
    private String stdin;
}

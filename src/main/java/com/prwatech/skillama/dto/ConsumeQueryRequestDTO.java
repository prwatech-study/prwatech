package com.prwatech.skillama.dto;

import lombok.Data;

@Data
public class ConsumeQueryRequestDTO {
    private String queryType; // CHAT, DEBUG, CODE_EXECUTION, GENERATE_CODE, LECTURE, MODULE_QUIZ
    private String courseId;
}

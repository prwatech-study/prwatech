package com.prwatech.skillama.dto;

import lombok.Data;

@Data
public class PracticeCodeRequestDTO {
    private String query;
    private String codeInstruction;
    private String courseId;
}

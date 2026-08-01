package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamAnswerResultDTO {
    private Integer questionId;
    private String questionText;
    private String selectedKey;
    private String selectedOptionText;
    private String correctKey;
    private String correctOptionText;
    private Boolean isCorrect;
    private String explanation;
    private List<ModuleQuizOptionDTO> options;
}

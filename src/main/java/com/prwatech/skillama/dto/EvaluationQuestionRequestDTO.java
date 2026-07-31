package com.prwatech.skillama.dto;

import com.prwatech.skillama.model.EvaluationQuestion;
import lombok.Data;

@Data
public class EvaluationQuestionRequestDTO {
    private EvaluationQuestion.Category category;
    private String questionText;
    private Integer order;
    private Boolean active;
}

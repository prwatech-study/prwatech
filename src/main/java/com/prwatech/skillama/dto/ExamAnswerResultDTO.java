package com.prwatech.skillama.dto;

import com.prwatech.skillama.model.ExamQuestionType;
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
    private ExamQuestionType questionType;
    private String selectedKey;
    private List<String> selectedKeys;
    private String selectedOptionText;
    private String correctKey;
    private List<String> correctKeys;
    private String correctOptionText;
    private Boolean isCorrect;
    private String explanation;
    private String domain;
    private List<ModuleQuizOptionDTO> options;
}

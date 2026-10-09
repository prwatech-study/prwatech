package com.prwatech.skillama.dto;

import com.prwatech.skillama.model.ExamQuestionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CertificationBankQuestionDTO {
    private String id;
    private String certificationExamId;
    private int bankVersion;
    private Integer questionId;
    private String question;
    private ExamQuestionType questionType;
    private List<ModuleQuizOptionDTO> options;
    private String correctKey;
    private List<String> correctKeys;
    private String explanation;
    private String domain;
    private LocalDateTime createdAt;
}

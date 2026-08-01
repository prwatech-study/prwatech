package com.prwatech.skillama.dto;

import com.prwatech.skillama.model.ExamDifficulty;
import com.prwatech.skillama.model.ExamType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StartExamResponseDTO {
    private String examSessionId;
    private String examTitle;
    private List<ModuleQuizQuestionDTO> questions;
    private Integer totalQuestions;
    private Integer timeLimitSeconds;
    private ExamDifficulty difficulty;
    private ExamType examType;
}

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
    /** Null on /start — questions are only handed out by /sessions/{id}/begin, when the clock starts. */
    private List<ModuleQuizQuestionDTO> questions;
    private Integer totalQuestions;
    private Integer timeLimitSeconds;
    /** Set by begin: seconds left on the (beganAt-anchored) clock — full limit on first begin. */
    private Integer remainingSeconds;
    private ExamDifficulty difficulty;
    private ExamType examType;
}

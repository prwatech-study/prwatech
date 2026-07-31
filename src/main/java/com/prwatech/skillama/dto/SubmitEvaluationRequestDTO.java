package com.prwatech.skillama.dto;

import com.prwatech.skillama.model.TesterEvaluationResponse;
import lombok.Data;

import java.util.List;

@Data
public class SubmitEvaluationRequestDTO {
    private String courseId;
    private String curriculumModuleId;
    private String submoduleId;
    private List<AnswerDTO> answers;

    @Data
    public static class AnswerDTO {
        private String questionId;
        private TesterEvaluationResponse.Answer answer;
        private String followUpComment;
    }
}

package com.prwatech.skillama.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateModuleQuizSessionResponseDTO {
    private String sessionId;
    private String quizTitle;
    private List<ModuleQuizQuestionDTO> questions;
    private Integer totalQuestions;
}

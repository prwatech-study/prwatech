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
public class CreateModuleQuizSessionRequestDTO {
    private String courseId;
    private String moduleName;
    private String quizTitle;
    private List<ModuleQuizQuestionDTO> questions;
}

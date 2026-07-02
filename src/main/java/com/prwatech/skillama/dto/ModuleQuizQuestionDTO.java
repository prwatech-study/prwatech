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
public class ModuleQuizQuestionDTO {
    private Integer id;
    private String question;
    private List<ModuleQuizOptionDTO> options;
    private String correctKey;
    private String explanation;
}

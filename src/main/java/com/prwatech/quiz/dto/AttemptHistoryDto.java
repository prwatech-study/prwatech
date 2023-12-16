package com.prwatech.quiz.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AttemptHistoryDto {

    private String quizName;
    private Integer totalQuestion;
    private Integer correct;
    private Integer unAttempted;
    private Integer incorrect;
}

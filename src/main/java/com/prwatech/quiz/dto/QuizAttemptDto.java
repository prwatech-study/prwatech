package com.prwatech.quiz.dto;

import com.prwatech.quiz.enumuration.ResultCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class QuizAttemptDto {

    private String quizContentId;
    private String userId;
    private Integer correctAns;
    private Integer wrongAns;
    private Integer timeTaken;
    private Integer attempt;
    private Integer percentage;
    private ResultCategory resultCategory;
    private List<QuizQuestionDto> quizQuestionDtoList;
}

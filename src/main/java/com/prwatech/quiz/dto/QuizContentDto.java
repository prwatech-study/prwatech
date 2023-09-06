package com.prwatech.quiz.dto;

import com.prwatech.quiz.enumuration.QuizCategory;
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
public class QuizContentDto {

    private QuizCategory quizCategory;
    private List<QuizQuestionDto> quizQuestionList;
}

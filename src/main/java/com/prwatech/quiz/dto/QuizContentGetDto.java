package com.prwatech.quiz.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class QuizContentGetDto extends QuizContentDto{

    private String id;
    private String quizId;
    private Integer passingMarks;
    private Integer totalMarks;
    private Integer time;
    private Boolean isPurchased;
}

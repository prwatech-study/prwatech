package com.prwatech.quiz.dto;


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
public class QuizDto {
    private String quizName;
    private String description;
    private String secondaryTitle;
    private List<String> whyThisQuiz;
    private String templateUrl;
}

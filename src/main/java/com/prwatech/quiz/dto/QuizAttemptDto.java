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
public class QuizAttemptDto {

    private String quizContentId;
    private String userId;
    private Integer correctAns;
    private Integer wrongAns;
    private Integer timeTakenHours;
    private Integer timeTakenMinute;
    private Integer timeTakenSecond;
    private Integer attempt;
    private Integer percentage;
}

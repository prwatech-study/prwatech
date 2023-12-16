package com.prwatech.quiz.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class QuizQuestionDto {

    @JsonProperty(value = "question")
    private String question;

    @JsonProperty(value = "option_a")
    private String optionA;

    @JsonProperty(value = "option_b")
    private String optionB;

    @JsonProperty(value = "option_c")
    private String optionC;

    @JsonProperty(value = "option_d")
    private String optionD;

    @JsonProperty(value = "answer")
    private String answer;

    @JsonProperty(value = "attempted_answer")
    private String attemptedAnswer;

    @JsonProperty(value = "description")
    private String description;
}

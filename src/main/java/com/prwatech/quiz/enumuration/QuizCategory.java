package com.prwatech.quiz.enumuration;

import com.fasterxml.jackson.annotation.JsonValue;

public enum QuizCategory {

    PAID("PAID"),
    UNPAID("UNPAID");
    private final String value;
    QuizCategory(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

}

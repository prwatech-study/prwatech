package com.prwatech.quiz.enumuration;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ResultCategory {

    GOOD("GOOD"),
    VERY_GOOD("VERY GOOD"),
    EXCELLENT("EXCELLENT");


    private final String value;
    ResultCategory(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}

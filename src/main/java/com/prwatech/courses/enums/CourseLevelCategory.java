package com.prwatech.courses.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum CourseLevelCategory {
  MOST_POPULAR("MOST_POPULAR"),
  FREE_COURSES("FREE_COURSES"),
  SELF_PLACED("SELF_PLACED"),
  ALL("ALL");

  private final String value;

  CourseLevelCategory(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }
}

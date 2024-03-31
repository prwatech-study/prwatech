package com.prwatech.courses.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum CourseLevelCategory {
  MOST_POPULAR("MOST_POPULAR", "Webinar"),
  FREE_COURSES("FREE_COURSES", "Webinar"),
  SELF_PLACED("SELF_PLACED","Online"),
  ALL("ALL", "Online");

  private final String value;
  private final String courseType;

  CourseLevelCategory(String value, String courseType) {
    this.value = value;
    this.courseType = courseType;
  }

  public static CourseLevelCategory fromString(String text) {
    for (CourseLevelCategory category : CourseLevelCategory.values()) {
      if (category.getValue().equalsIgnoreCase(text)) {
        return category;
      }
    }
    return null; // Or throw an IllegalArgumentException
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  public String getCourseType() {
    return courseType;
  }
}

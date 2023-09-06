package com.prwatech.user.enumuration;

import com.fasterxml.jackson.annotation.JsonValue;

public enum UserGender {
  MALE("MALE"),
  FEMALE("FEMALE"),
  OTHER("OTHER");

  private final String value;

  UserGender(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }
}

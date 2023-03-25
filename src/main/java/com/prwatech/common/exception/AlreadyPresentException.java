package com.prwatech.common.exception;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.ALREADY_REPORTED)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AlreadyPresentException extends RuntimeException {

  public AlreadyPresentException(String message) {
    super(message);
  }
}

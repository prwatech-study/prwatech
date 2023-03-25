package com.prwatech.common.exception;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@JsonIgnoreProperties(ignoreUnknown = true)
@ResponseStatus(HttpStatus.NOT_FOUND)
public class NotFoundException extends RuntimeException {

  public NotFoundException(String message) {
    super(message);
  }
}

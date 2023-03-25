package com.prwatech.common.exception;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@JsonIgnoreProperties(ignoreUnknown = true)
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class UnProcessableEntityException extends RuntimeException {

  public UnProcessableEntityException(String message) {
    super(message);
  }
}

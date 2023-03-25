package com.prwatech.common.exception;

import static com.prwatech.common.Constants.ERROR;

import com.prwatech.common.dto.HttpResponse;
import com.prwatech.common.dto.ResponseMessage;
import javax.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

  private static final org.slf4j.Logger LOGGER =
      org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(UnAuthorizedException.class)
  public ResponseEntity<ResponseMessage> handleUnAuthorizedException(
      UnAuthorizedException unAuthorizedException) {
    HttpResponse errorResponse =
        new HttpResponse(HttpStatus.UNAUTHORIZED, unAuthorizedException.getMessage());

    // Customise the log format below according to the need
    LOGGER.error(unAuthorizedException.getMessage(), unAuthorizedException);
    return new ResponseEntity<>(new ResponseMessage(ERROR, errorResponse), HttpStatus.UNAUTHORIZED);
  }

  @ExceptionHandler(UnProcessableEntityException.class)
  public ResponseEntity<ResponseMessage> handleUnProcessableException(
      UnProcessableEntityException unProcessableEntityException) {
    HttpResponse errorResponse =
        new HttpResponse(
            HttpStatus.UNPROCESSABLE_ENTITY, unProcessableEntityException.getMessage());
    return new ResponseEntity<>(
        new ResponseMessage(ERROR, errorResponse), HttpStatus.UNPROCESSABLE_ENTITY);
  }

  @ExceptionHandler(BadRequestException.class)
  public ResponseEntity<ResponseMessage> handleBadRequestException(
      BadRequestException badRequestException) {
    HttpResponse errorResponse =
        new HttpResponse(HttpStatus.BAD_REQUEST, badRequestException.getMessage());
    return new ResponseEntity<>(new ResponseMessage(ERROR, errorResponse), HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<ResponseMessage> handleNotFoundException(
      NotFoundException notFoundException) {
    HttpResponse errorResponse =
        new HttpResponse(HttpStatus.NOT_FOUND, notFoundException.getMessage());

    return new ResponseEntity<>(new ResponseMessage(ERROR, errorResponse), HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(LockedException.class)
  public ResponseEntity<ResponseMessage> handleLockedException(LockedException lockedException) {
    HttpResponse errorResponse =
        new HttpResponse(HttpStatus.UNAUTHORIZED, lockedException.getMessage());

    // Customise the log format below according to the need
    LOGGER.error(lockedException.getMessage(), lockedException);
    return new ResponseEntity<>(new ResponseMessage(ERROR, errorResponse), HttpStatus.UNAUTHORIZED);
  }

  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<ResponseMessage> handleForbiddenException(
      ForbiddenException forbiddenException, HttpServletResponse response) {
    HttpResponse errorResponse =
        new HttpResponse(HttpStatus.FORBIDDEN, forbiddenException.getMessage());

    // Customise the log format below according to the need
    LOGGER.error(forbiddenException.getMessage(), forbiddenException);
    return new ResponseEntity<>(new ResponseMessage(ERROR, errorResponse), HttpStatus.FORBIDDEN);
  }

  @ExceptionHandler(AlreadyPresentException.class)
  public ResponseEntity<ResponseMessage> handleAlreadyPresentException(
      AlreadyPresentException alreadyPresentException) {

    HttpResponse errorResponse =
        new HttpResponse(HttpStatus.ALREADY_REPORTED, alreadyPresentException.getMessage());

    LOGGER.error(alreadyPresentException.getMessage(), alreadyPresentException);
    return new ResponseEntity<>(
        new ResponseMessage(ERROR, errorResponse), HttpStatus.ALREADY_REPORTED);
  }
}

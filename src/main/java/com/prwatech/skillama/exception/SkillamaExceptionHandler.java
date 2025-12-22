package com.prwatech.skillama.exception;

import com.prwatech.common.exception.NotFoundException;
import com.prwatech.skillama.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Exception handler for Skillama module endpoints
 * Provides consistent error response format for /api/* endpoints
 */
@RestControllerAdvice(basePackages = "com.prwatech.skillama.controller")
public class SkillamaExceptionHandler {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SkillamaExceptionHandler.class);
    
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex) {
        LOGGER.error("Resource not found: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse(
            HttpStatus.NOT_FOUND.value(),
            "Not Found",
            ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
    
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFoundException(NotFoundException ex) {
        LOGGER.error("Not found: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse(
            HttpStatus.NOT_FOUND.value(),
            "NOT_FOUND",
            ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
    
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        LOGGER.error("Bad request: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            "Bad Request",
            ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex) {
        LOGGER.error("Runtime error: {}", ex.getMessage(), ex);
        
        // Check for specific error messages
        String message = ex.getMessage();
        HttpStatus status = HttpStatus.BAD_REQUEST;
        String error = "Bad Request";
        
        if (message != null) {
            if (message.contains("Email already exists") || message.contains("already exists")) {
                status = HttpStatus.CONFLICT;
                error = "Conflict";
            } else if (message.contains("Only OWNER") || message.contains("Access denied") || message.contains("Forbidden")) {
                status = HttpStatus.FORBIDDEN;
                error = "Forbidden";
            } else if (message.contains("not found") || message.contains("Not found")) {
                status = HttpStatus.NOT_FOUND;
                error = "Not Found";
            } else if (message.contains("Unauthorized") || message.contains("Invalid token") || message.contains("Token")) {
                status = HttpStatus.UNAUTHORIZED;
                error = "Unauthorized";
            }
        }
        
        ErrorResponse errorResponse = new ErrorResponse(
            status.value(),
            error,
            message != null ? message : "An error occurred"
        );
        return ResponseEntity.status(status).body(errorResponse);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        LOGGER.error("Unexpected error: {}", ex.getMessage(), ex);
        ErrorResponse error = new ErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "Internal Server Error",
            "An unexpected error occurred"
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}


package com.kai.applicationlogparser.api;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidTimezoneException.class)
    public ResponseEntity<Map<String, String>> handleInvalidTimezone(InvalidTimezoneException ex) {
        return ResponseEntity.status(HttpStatus.FAILED_DEPENDENCY)
                .body(Map.of("message", ex.getMessage()));
    }
}

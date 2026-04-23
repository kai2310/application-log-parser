package com.kai.applicationlogparser.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler subject = new ApiExceptionHandler();

    @Test
    void handleInvalidTimezoneReturnsFailedDependency() {
        InvalidTimezoneException exception = new InvalidTimezoneException("Mars/Phobos", new IllegalArgumentException("bad zone"));

        ResponseEntity<Map<String, String>> response = subject.handleInvalidTimezone(exception);

        assertEquals(HttpStatus.FAILED_DEPENDENCY, response.getStatusCode());
        assertEquals("timezone must be a valid java.time.ZoneId: Mars/Phobos", response.getBody().get("message"));
    }
}

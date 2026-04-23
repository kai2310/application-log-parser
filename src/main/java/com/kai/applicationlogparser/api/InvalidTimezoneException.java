package com.kai.applicationlogparser.api;

public class InvalidTimezoneException extends RuntimeException {

    public InvalidTimezoneException(String timezone, Throwable cause) {
        super("timezone must be a valid java.time.ZoneId: " + timezone, cause);
    }
}

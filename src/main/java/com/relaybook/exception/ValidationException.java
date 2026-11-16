package com.relaybook.exception;

/** A user-submitted value broke a domain rule; mapped to HTTP 400. */
public class ValidationException extends RelayBookException {
    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}

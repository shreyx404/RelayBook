package com.relaybook.exception;

/**
 * Abstract root of RelayBook's unchecked exception hierarchy.
 *
 * <p>Domain and persistence code throws subclasses of this type, so the web
 * layer can map a whole family of failures to HTTP status codes with one
 * catch block, and callers can catch every RelayBook failure without also
 * catching unrelated runtime bugs.</p>
 */
public abstract class RelayBookException extends RuntimeException {
    protected RelayBookException(String message) {
        super(message);
    }

    protected RelayBookException(String message, Throwable cause) {
        super(message, cause);
    }
}

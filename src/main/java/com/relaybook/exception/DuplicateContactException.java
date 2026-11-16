package com.relaybook.exception;

/**
 * Creating this record would duplicate an existing one (same name and a
 * shared route); mapped to HTTP 409 Conflict.
 */
public class DuplicateContactException extends RelayBookException {
    public DuplicateContactException(String message) {
        super(message);
    }
}

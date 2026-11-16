package com.relaybook.repository;

import com.relaybook.exception.RelayBookException;

/** Unchecked wrapper for persistence failures; part of the RelayBook hierarchy. */
public class DataAccessException extends RelayBookException {
    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }

    public DataAccessException(String message) {
        super(message);
    }
}

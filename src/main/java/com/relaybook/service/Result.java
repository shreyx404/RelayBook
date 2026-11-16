package com.relaybook.service;

import java.util.Objects;

/**
 * Generic result type: carries either a success value of type T or an error
 * message, never both. Used where a bulk operation can fail per item and the
 * caller wants to continue instead of throwing — for example the vCard
 * importer, which parses one card at a time and collects the outcomes.
 */
public final class Result<T> {
    private final T value;
    private final String error;

    private Result(T value, String error) {
        this.value = value;
        this.error = error;
    }

    public static <T> Result<T> success(T value) {
        return new Result<>(Objects.requireNonNull(value), null);
    }

    public static <T> Result<T> failure(String error) {
        return new Result<>(null, Objects.requireNonNull(error));
    }

    public boolean isSuccess() {
        return error == null;
    }

    /** The success value; throws when this is a failure. */
    public T getValue() {
        if (!isSuccess()) {
            throw new IllegalStateException("No value: this result is a failure (" + error + ")");
        }
        return value;
    }

    /** The error message; throws when this is a success. */
    public String getError() {
        if (isSuccess()) {
            throw new IllegalStateException("No error: this result is a success.");
        }
        return error;
    }

    @Override
    public String toString() {
        return isSuccess() ? "Result[" + value + "]" : "Result[error: " + error + "]";
    }
}

package com.example.engine.common.exception;

/** Signals that retrying would never succeed, so the message goes straight to the parking lot. */
public class PermanentFailureException extends RuntimeException {

    public PermanentFailureException(String message) {
        super(message);
    }
}

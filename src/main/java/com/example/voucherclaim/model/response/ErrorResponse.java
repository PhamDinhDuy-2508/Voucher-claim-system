package com.example.voucherclaim.model.response;

import java.time.Instant;

public class ErrorResponse {
    private final String code;
    private final String message;
    private final boolean retryable;
    private final Instant timestamp;

    public ErrorResponse(String code, String message, boolean retryable, Instant timestamp) {
        this.code = code;
        this.message = message;
        this.retryable = retryable;
        this.timestamp = timestamp;
    }

    public String getCode() { return code; }
    public String getMessage() { return message; }
    public boolean isRetryable() { return retryable; }
    public Instant getTimestamp() { return timestamp; }
}

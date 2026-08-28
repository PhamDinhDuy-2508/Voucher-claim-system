package com.example.voucherclaim.exception;

import org.springframework.http.HttpStatus;

public class ServiceException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final boolean retryable;

    public ServiceException(HttpStatus status, String code, String message, boolean retryable) {
        super(message);
        this.status = status;
        this.code = code;
        this.retryable = retryable;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public boolean retryable() {
        return retryable;
    }

    public static ServiceException notFound(String code, String message) {
        return new ServiceException(HttpStatus.NOT_FOUND, code, message, false);
    }

    public static ServiceException conflict(String code, String message) {
        return new ServiceException(HttpStatus.CONFLICT, code, message, false);
    }

    public static ServiceException forbidden(String code, String message) {
        return new ServiceException(HttpStatus.FORBIDDEN, code, message, false);
    }

    public static ServiceException busy(String message) {
        return new ServiceException(HttpStatus.SERVICE_UNAVAILABLE, "CLAIM_BUSY", message, true);
    }
}

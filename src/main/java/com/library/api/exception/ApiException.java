package com.library.api.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class for every error this API deliberately raises. Carrying the status and a
 * machine-readable errorCode on the exception keeps the handler tiny and keeps
 * HTTP concerns out of the services' control flow.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    protected ApiException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}

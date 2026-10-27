package com.lordsai.lsi.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class for business failures that carry a safe, user-facing message
 * and an explicit HTTP status. Never wrap internal details in these messages.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}

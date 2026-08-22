package com.library.api.exception;

import org.springframework.http.HttpStatus;

/** 400 - a LIBRARIAN must say which borrower they are acting on behalf of. */
public class BorrowerIdRequiredException extends ApiException {

    public BorrowerIdRequiredException() {
        super(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "borrowerId is required when acting on behalf of a member");
    }
}

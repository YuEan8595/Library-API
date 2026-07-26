package com.library.api.exception;

import org.springframework.http.HttpStatus;

/** 409 - a borrower is already registered under this email address. */
public class EmailAlreadyRegisteredException extends ApiException {

    public EmailAlreadyRegisteredException(String email) {
        super(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED",
                "A borrower is already registered with email '" + email + "'");
    }
}

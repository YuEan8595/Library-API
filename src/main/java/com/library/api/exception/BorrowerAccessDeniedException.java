package com.library.api.exception;

import org.springframework.http.HttpStatus;

/** 403 - a MEMBER tried to read another borrower's record or loans. */
public class BorrowerAccessDeniedException extends ApiException {

    public BorrowerAccessDeniedException(Long borrowerId) {
        super(HttpStatus.FORBIDDEN, "BORROWER_ACCESS_DENIED",
                "You may only access your own borrower record, not borrower " + borrowerId);
    }
}

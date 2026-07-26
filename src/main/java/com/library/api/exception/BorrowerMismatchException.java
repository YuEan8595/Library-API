package com.library.api.exception;

import org.springframework.http.HttpStatus;

/** 403 - only the borrower holding the loan may return it. */
public class BorrowerMismatchException extends ApiException {

    public BorrowerMismatchException(Long bookId, Long borrowerId) {
        super(HttpStatus.FORBIDDEN, "BORROWER_MISMATCH",
                "Borrower " + borrowerId + " does not hold the active loan for book " + bookId);
    }
}

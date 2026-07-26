package com.library.api.exception;

import org.springframework.http.HttpStatus;

/** 409 - this exact copy is already on loan to someone. */
public class BookAlreadyBorrowedException extends ApiException {

    public BookAlreadyBorrowedException(Long bookId) {
        super(HttpStatus.CONFLICT, "BOOK_ALREADY_BORROWED",
                "Book " + bookId + " is currently borrowed and cannot be borrowed again until it is returned");
    }
}

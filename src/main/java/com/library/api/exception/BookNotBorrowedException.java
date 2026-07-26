package com.library.api.exception;

import org.springframework.http.HttpStatus;

/** 409 - a return was attempted on a copy that is sitting on the shelf. */
public class BookNotBorrowedException extends ApiException {

    public BookNotBorrowedException(Long bookId) {
        super(HttpStatus.CONFLICT, "BOOK_NOT_BORROWED",
                "Book " + bookId + " is not currently borrowed, so it cannot be returned");
    }
}

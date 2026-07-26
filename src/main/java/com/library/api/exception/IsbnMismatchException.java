package com.library.api.exception;

import org.springframework.http.HttpStatus;

/**
 * 409 - the submitted title/author disagree with what is already on file for this ISBN.
 * Enforces "two books with the same ISBN must have the same title and author".
 */
public class IsbnMismatchException extends ApiException {

    public IsbnMismatchException(String isbn, String existingTitle, String existingAuthor) {
        super(HttpStatus.CONFLICT, "ISBN_MISMATCH",
                "ISBN " + isbn + " is already registered as '" + existingTitle + "' by " + existingAuthor
                        + ". Books sharing an ISBN must have the same title and author.");
    }
}

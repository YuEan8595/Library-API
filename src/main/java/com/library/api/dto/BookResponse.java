package com.library.api.dto;

import com.library.api.domain.BookCopy;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A single copy as seen by API consumers. id is the copy id: it is what
 * borrow and return operate on, and it differs between two copies of the same ISBN.
 */
@Schema(description = "A physical book copy in the library")
public record BookResponse(
        Long id,
        String isbn,
        String title,
        String author,
        @Schema(description = "false when the copy is currently on loan") boolean available) {

    public static BookResponse from(BookCopy copy, boolean available) {
        return new BookResponse(
                copy.getId(),
                copy.getEdition().getIsbn(),
                copy.getEdition().getTitle(),
                copy.getEdition().getAuthor(),
                available);
    }
}

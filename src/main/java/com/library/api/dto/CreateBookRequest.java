package com.library.api.dto;

import com.library.api.validation.Isbn;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload for registering a new physical copy of a book")
public record CreateBookRequest(

        @Schema(description = "ISBN-10 or ISBN-13. Hyphens and spaces are allowed and stripped.",
                example = "978-0-13-235088-4")
        @NotBlank(message = "isbn must not be blank")
        @Isbn(message = "isbn must be a valid ISBN-10 or ISBN-13, including its check digit")
        String isbn,

        @Schema(example = "Clean Code")
        @NotBlank(message = "title must not be blank")
        @Size(max = 255, message = "title must not exceed 255 characters")
        String title,

        @Schema(example = "Robert C. Martin")
        @NotBlank(message = "author must not be blank")
        @Size(max = 255, message = "author must not exceed 255 characters")
        String author
) {
}

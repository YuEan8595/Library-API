package com.library.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Identifies the borrower a borrow/return is performed on behalf of")
public record BorrowRequest(

        @Schema(example = "1")
        @NotNull(message = "borrowerId must not be null")
        @Positive(message = "borrowerId must be a positive number")
        Long borrowerId
) {
}

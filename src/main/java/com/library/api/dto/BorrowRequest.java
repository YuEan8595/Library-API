package com.library.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;

@Schema(description = "Identifies the borrower a borrow/return is performed on behalf of. "
        + "Required for a LIBRARIAN acting on a member's behalf; ignored for a MEMBER, whose "
        + "own borrower id is taken from their access token.")
public record BorrowRequest(

        @Schema(example = "1")
        @Positive(message = "borrowerId must be a positive number")
        Long borrowerId
) {
}

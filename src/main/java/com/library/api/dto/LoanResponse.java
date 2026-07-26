package com.library.api.dto;

import com.library.api.domain.BorrowRecord;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "The outcome of a borrow or return operation")
public record LoanResponse(
        Long loanId,
        Long bookId,
        String isbn,
        String title,
        Long borrowerId,
        String borrowerName,
        Instant borrowedAt,
        @Schema(description = "null while the loan is still active") Instant returnedAt) {

    public static LoanResponse from(BorrowRecord record) {
        return new LoanResponse(
                record.getId(),
                record.getBookCopy().getId(),
                record.getBookCopy().getEdition().getIsbn(),
                record.getBookCopy().getEdition().getTitle(),
                record.getBorrower().getId(),
                record.getBorrower().getName(),
                record.getBorrowedAt(),
                record.getReturnedAt());
    }
}

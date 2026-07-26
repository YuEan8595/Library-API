package com.library.api.dto;

import com.library.api.domain.Borrower;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A registered library member")
public record BorrowerResponse(Long id, String name, String email) {

    public static BorrowerResponse from(Borrower borrower) {
        return new BorrowerResponse(borrower.getId(), borrower.getName(), borrower.getEmail());
    }
}
